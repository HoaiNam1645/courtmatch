import json
import shutil

import pytest
from fastapi.testclient import TestClient

from courtly_ai import config, dataset, inference, registry, service, trainers
from courtly_ai.features import PARTNER_FEATURES

FAST = {"iterations": 25, "depth": 3, "early_stopping_rounds": 10}
TOKEN = "test-token"


@pytest.fixture(scope="module")
def trained(tmp_path_factory, data):
    config.ARTIFACT_DIR = tmp_path_factory.mktemp("artifacts")
    partner = trainers.train_partner(data, seed=5, params=FAST)
    team = trainers.train_team(data, seed=5, params=FAST)
    return partner, team


def client() -> TestClient:
    return TestClient(service.create_app(TOKEN))


def headers():
    return {"X-Internal-Token": TOKEN}


def test_training_is_reproducible_and_writes_artifacts(data, trained):
    partner, _ = trained
    again = trainers.train_partner(data, seed=5, params=FAST, save=False)
    assert again["metadata"]["metrics"]["model"]["overall"] == partner["metadata"]["metrics"]["model"]["overall"]
    directory = registry.artifact_dir(partner["version"])
    meta = json.loads((directory / "metadata.json").read_text())
    assert meta["checksum"] == registry.sha256(directory / "model.cbm")
    assert meta["schema"]["features"] == PARTNER_FEATURES and meta["seed"] == 5 and "gate" in meta
    assert (directory / "feature_importance.json").is_file()


def test_rejects_missing_or_wrong_token_and_refuses_to_start_without_token(trained):
    with pytest.raises(RuntimeError):
        service.create_app(None)
    c = client()
    assert c.post("/v1/rank-partners", json={"requesterId": "r", "candidates": []}).status_code == 401
    assert c.post("/v1/rank-partners", headers={"X-Internal-Token": "nope"}, json={"requesterId": "r", "candidates": []}).status_code == 401
    assert c.get("/health").json()["models"] == {}


def test_load_rank_and_health(data, trained):
    partner, team = trained
    c = client()
    ok = c.post("/v1/models/load", headers=headers(), json={"modelType": "catboost_ranker", "modelVersion": partner["version"], "checksum": partner["metadata"].get("checksum")})
    assert ok.status_code == 200, ok.text
    health = c.get("/health").json()
    assert health["models"]["partner"]["modelVersion"] == partner["version"]

    groups = dataset.build_partner_groups(data, seed=5)
    g = groups[0]
    body = {"requesterId": g.requester, "candidates": [{"userId": r.candidate, "features": {k: (None if v != v else v) for k, v in r.values.items()}} for r in g.rows]}
    res = c.post("/v1/rank-partners", headers=headers(), json=body)
    assert res.status_code == 200, res.text
    out = res.json()
    assert out["modelVersion"] == partner["version"] and out["scoringSource"] == "ai"
    assert {r["userId"] for r in out["results"]} == {r.candidate for r in g.rows}  # khong them/bot ung vien
    for r in out["results"]:
        assert 0 <= r["totalScore"] <= 100 and 0 < r["confidence"] <= 1 and len(r["reasons"]) <= 3


def test_rank_validation(data, trained):
    partner, _ = trained
    c = client()
    assert c.post("/v1/rank-partners", headers=headers(), json={"requesterId": "r", "candidates": [{"userId": "x", "features": {}}]}).status_code == 503
    c.post("/v1/models/load", headers=headers(), json={"modelType": "catboost_ranker", "modelVersion": partner["version"]})
    full = {name: 1.0 for name in PARTNER_FEATURES}
    assert c.post("/v1/rank-partners", headers=headers(), json={"requesterId": "r", "candidates": [{"userId": "x", "features": {"r_rating": 1000.0}}]}).status_code == 422  # thieu bat buoc
    assert c.post("/v1/rank-partners", headers=headers(), json={"requesterId": "r", "candidates": [{"userId": "x", "features": {**full, "unknown": 1.0}}]}).status_code == 422
    too_many = [{"userId": str(i), "features": full} for i in range(201)]
    assert c.post("/v1/rank-partners", headers=headers(), json={"requesterId": "r", "candidates": too_many}).status_code == 422
    assert c.post("/v1/rank-partners", headers=headers(), json={"requesterId": "r", "candidates": []}).status_code == 422


def test_load_guards(data, trained, tmp_path):
    partner, team = trained
    c = client()
    assert c.post("/v1/models/load", headers=headers(), json={"modelType": "catboost_ranker", "modelVersion": "khong-co"}).status_code == 404
    assert c.post("/v1/models/load", headers=headers(), json={"modelType": "catboost_ranker", "modelVersion": "../etc"}).status_code == 422
    assert c.post("/v1/models/load", headers=headers(), json={"modelType": "bad", "modelVersion": partner["version"]}).status_code == 422
    assert c.post("/v1/models/load", headers=headers(), json={"modelType": "catboost_team_ranker", "modelVersion": partner["version"]}).status_code == 409
    assert c.post("/v1/models/load", headers=headers(), json={"modelType": "catboost_ranker", "modelVersion": partner["version"], "checksum": "0" * 64}).status_code == 409
    # file bi sua -> checksum khong khop
    broken = registry.artifact_dir(partner["version"]) / "model.cbm"
    shutil.copy(broken, tmp_path / "backup.cbm")
    broken.write_bytes(broken.read_bytes() + b"x")
    assert c.post("/v1/models/load", headers=headers(), json={"modelType": "catboost_ranker", "modelVersion": partner["version"]}).status_code == 409
    shutil.copy(tmp_path / "backup.cbm", broken)


def test_predict_balance_is_symmetric(data, trained):
    _, team = trained
    c = client()
    c.post("/v1/models/load", headers=headers(), json={"modelType": "catboost_team_ranker", "modelVersion": team["version"]})
    row = dataset.build_team_rows(data)[0].values
    from courtly_ai.pairs import swap_teams
    clean = lambda v: {k: (None if x != x else x) for k, x in v.items()}
    res = c.post("/v1/predict-balance", headers=headers(), json={"options": [{"optionId": "a", "features": clean(row)}, {"optionId": "b", "features": clean(swap_teams(row))}]})
    assert res.status_code == 200, res.text
    a, b = res.json()["results"]
    assert abs(a["team1WinProbability"] + b["team1WinProbability"] - 1) < 1e-3
    assert a["balance"] == b["balance"] and 0 <= a["balance"] <= 100

from datetime import timedelta

from courtly_ai import dataset
from courtly_ai.history import Timelines


def test_snapshot_only_uses_matches_before_t(data):
    timelines = Timelines(data)
    match = next(m for m in data.matches if "p3" in m.team1 + m.team2)
    before = timelines.snapshot("p3", match.t)          # tran dang xet CHUA duoc tinh
    after = timelines.snapshot("p3", match.t + timedelta(seconds=1))
    assert after.matches == before.matches + 1


def test_groups_split_by_time_without_overlap(data):
    groups = dataset.build_partner_groups(data, seed=7)
    assert len(groups) > 60
    split = dataset.split_by_time(groups)
    assert max(g.at for g in split["train"]) < min(g.at for g in split["valid"]) <= max(g.at for g in split["valid"]) < min(g.at for g in split["test"])
    assert all(any(r.label > 0 for r in g.rows) for g in groups)
    assert all(len({r.candidate for r in g.rows}) == len(g.rows) for g in groups)


def test_dataset_is_reproducible(data):
    first = dataset.build_partner_groups(data, seed=11)
    second = dataset.build_partner_groups(data, seed=11)
    assert [(g.requester, g.at, [(r.candidate, r.label) for r in g.rows]) for g in first] == \
           [(g.requester, g.at, [(r.candidate, r.label) for r in g.rows]) for g in second]


def test_labels_use_only_future_matches(data):
    groups = dataset.build_partner_groups(data, seed=3)
    timelines = Timelines(data)
    g = groups[0]
    row = g.rows[0]
    # dac trung cua nhom khong doi khi them tran SAU khoang nhan: snapshot tai T chi dua tren qua khu
    assert timelines.snapshot(g.requester, g.at).matches == row.values["r_matches"]


def test_team_rows_are_symmetric_when_swapped(data):
    rows = dataset.build_team_rows(data)
    assert rows
    from courtly_ai.pairs import swap_teams
    v = rows[0].values
    twice = swap_teams(swap_teams(v))
    assert {k: v[k] for k in v} == twice

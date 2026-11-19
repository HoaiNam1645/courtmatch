"""Schema dac trung DUY NHAT cho huan luyen va suy luan (2.2.11, 2.2.12).

Thu tu va ten dac trung duoc luu vao metadata.json / ai_models.feature_schema_json; service tu choi tai model neu schema trong file
khac schema trong ma nguon. Gia tri thieu de None -> NaN (khong dien trung binh, QUY-TAC 2.2.4).
"""

SCHEMA_VERSION = 1

# Dac trung cua mot cap (nguoi tim r, ung vien c).
PARTNER_FEATURES = [
    "r_rating",
    "c_rating",
    "rating_gap",
    "r_win_rate",
    "c_win_rate",
    "r_matches",
    "c_matches",
    "r_stability",
    "c_stability",
    "r_recency_days",
    "c_recency_days",
    "r_avg_point_diff",
    "c_avg_point_diff",
    "skill_similarity",
    "location_score",
    "time_score",
    "style_score",
    "distance_km",
    "same_play_type",
    "r_provisional",
    "c_provisional",
]

# Dac trung cua mot phuong an chia doi (doi 1 vs doi 2); mau huan luyen duoc doi xung (doi cho hai doi).
TEAM_FEATURES = [
    "t1_rating",
    "t2_rating",
    "rating_diff",
    "t1_partner_gap",
    "t2_partner_gap",
    "t1_win_rate",
    "t2_win_rate",
    "t1_min_matches",
    "t2_min_matches",
    "t1_stability",
    "t2_stability",
    "t1_pair_compat",
    "t2_pair_compat",
    "t1_recency_days",
    "t2_recency_days",
    "is_mixed",
]

SCHEMAS = {"partner": PARTNER_FEATURES, "team": TEAM_FEATURES}

# Dac trung cho phep NaN (khong co du lieu); con lai luon co so.
NAN_ALLOWED = {
    "partner": {"r_stability", "c_stability", "r_recency_days", "c_recency_days", "r_avg_point_diff",
                "c_avg_point_diff", "location_score", "time_score", "distance_km"},
    "team": {"t1_stability", "t2_stability", "t1_pair_compat", "t2_pair_compat", "t1_recency_days", "t2_recency_days"},
}


def schema_document(model_kind: str) -> dict:
    return {"version": SCHEMA_VERSION, "kind": model_kind, "features": SCHEMAS[model_kind]}

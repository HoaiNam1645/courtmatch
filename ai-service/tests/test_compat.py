from courtly_ai import compat


def test_skill_similarity_matches_spring_formula():
    assert compat.skill_similarity(1000, 1000) == 100.0
    assert compat.skill_similarity(1000, 1400) == round(100 * (1 - abs(2 * (1 / (1 + 10)) - 1)), 2) == 18.18


def test_location_and_style():
    assert compat.location_compat(0, 5) == 100.0
    assert compat.location_compat(2.5, 5) == 50.0
    assert compat.location_compat(9, 5) == 0.0
    assert compat.location_compat(None, 5) is None
    assert compat.style_compat("attacking", "attacking") == 100.0
    assert compat.style_compat("balanced", "defensive") == 80.0
    assert compat.style_compat("attacking", "defensive") == 65.0
    assert compat.style_compat(None, "attacking") == 50.0


def test_time_compat_counts_requested_slot_and_missing_schedule():
    requester = [(2, 18 * 60, 20 * 60)]
    assert compat.time_compat(requester, [(2, 18 * 60, 20 * 60)], None) == 100.0
    assert compat.time_compat(requester, [(2, 19 * 60, 20 * 60)], None) == 50.0
    # khung yeu cau them vao lich cua nguoi tim: 120 + 60 phut, ung vien chi phu 120 phut dau
    assert compat.time_compat(requester, [(2, 18 * 60, 20 * 60)], (4, 18 * 60, 19 * 60)) == round(100 * 120 / 180, 2)
    assert compat.time_compat(requester, [], None) is None


def test_baseline_total_drops_missing_components_and_renormalises():
    assert compat.baseline_total(100, 100, 100, 100) == 100.0
    assert compat.baseline_total(80, 60, None, None) == round((0.4 * 80 + 0.2 * 60) / 0.6, 2)
    assert compat.baseline_total(None, None, None, None) == 0.0

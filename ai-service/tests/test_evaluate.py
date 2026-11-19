from courtly_ai import evaluate


def test_perfect_and_worst_ranking():
    labels = [4, 3, 0, 0, 2]
    perfect = evaluate.group_metrics(labels, [5, 4, 1, 0, 3])
    assert perfect["ndcg@5"] == 1.0
    worst = evaluate.group_metrics(labels, [0, 1, 5, 4, 2])
    assert worst["ndcg@3"] < perfect["ndcg@3"]


def test_precision_recall_use_relevant_threshold():
    labels = [4, 1, 2, 0]  # lien quan = nhan >= 2 -> 2 ung vien
    metrics = evaluate.group_metrics(labels, [4, 3, 2, 1])
    assert metrics["precision@3"] == 2 / 3
    assert metrics["recall@3"] == 1.0


def test_binary_metrics():
    m = evaluate.binary_metrics([1, 0, 1, 0], [0.9, 0.2, 0.6, 0.4])
    assert m["accuracy"] == 1.0 and m["log_loss"] < 0.5 and m["samples"] == 4

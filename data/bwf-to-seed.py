#!/usr/bin/env python3
"""
Boc tach bo du lieu BWF (data/bwf-match-data) thanh du lieu seed cho Courtly.

Chay:  python3 data/bwf-to-seed.py

Sinh ra hai file trong backend/src/main/resources/seed/:
    bwf-players.json   nguoi choi + thong ke da tinh san
    bwf-matches.json   tran dau + ty so tung set

VI SAO TINH TOAN O DAY CHU KHONG O SEEDER
Quy tac du an (QUY-TAC-LAM-CHUC-NANG.md): seeder chi chen du lieu co san, khong
chay thuat toan. Diem Elo va thong ke deu duoc tinh o script nay roi ghi thanh
hang so vao JSON - giong cach MatchSeeder hien tai lam voi 6 tran viet tay.

Cong thuc Elo lay dung theo thiet-ke-sprint-2-du-lieu-va-hieu-suat.md muc 2.2.6.
Khi nao cai dat 2.2.6 bang Java, no phai cho ra dung nhung con so trong file
JSON nay - dung lam bo kiem thu doi chieu.
"""

import csv
import json
import math
import re
import unicodedata
from collections import Counter, defaultdict
from datetime import date, datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "data" / "bwf-match-data" / "matches.csv"
OUT_DIR = ROOT / "backend" / "src" / "main" / "resources" / "seed"

# --- Tham so chon mau -------------------------------------------------------
# Muc tieu theo thiet ke Sprint 2: 150-300 nguoi choi, moi nguoi 10-20 tran.
FROM_DATE = "2024-01-01"      # chi lay giai gan day de nguoi choi con "dang hoat dong"
TARGET_PLAYERS = 200          # so van dong vien dua vao bo seed
MIN_MATCHES = 8               # duoi nguong nay thi Elo chua kip hoi tu
MOC_HOM_NAY = date(2026, 7, 6)  # ngay cuoi cua bo du lieu, dung de quy ra "daysAgo"

# --- Tham so Elo (thiet-ke-sprint-2 muc 2.2.6) ------------------------------
DIEM_KHOI_TAO = 1000.0
SAN_DIEM = 100.0

def he_so_k(so_tran_da_choi: int) -> int:
    if so_tran_da_choi < 10:
        return 40
    if so_tran_da_choi < 30:
        return 24
    return 16

DISCIPLINE_TO_TYPE = {
    "MS": "single", "WS": "single",
    "MD": "double", "WD": "double",
    "XD": "mixed_double",
}
DISCIPLINE_TO_GENDER = {"MS": "male", "MD": "male", "WS": "female", "WD": "female"}


def tach_nguoi_choi(o_doi: str):
    return [p.strip() for p in o_doi.split("/") if p.strip()]


def doc_du_lieu():
    with SOURCE.open(encoding="utf-8") as f:
        for row in csv.DictReader(f):
            # Bo vong loai: chat luong doi thu chenh lech qua lon, lam nhieu diem Elo.
            if row["round"].startswith("Q"):
                continue
            if row["date"] < FROM_DATE:
                continue
            yield row


def chon_nguoi_choi(rows):
    """Chon TARGET_PLAYERS nguoi nhieu tran nhat, roi lap lai cho on dinh.

    Lan dau chon theo so tran tren toan bo du lieu. Nhung khi da gioi han tap
    nguoi choi, nhieu tran bi loai (vi co doi thu ngoai tap), nen so tran thuc
    te cua moi nguoi giam xuong. Lap lai vai vong de tap nguoi choi va tap tran
    khop nhau.
    """
    chon = None
    for vong in range(6):
        dem = Counter()
        for r in rows:
            nguoi = tach_nguoi_choi(r["team1"]) + tach_nguoi_choi(r["team2"])
            if chon is not None and not all(p in chon for p in nguoi):
                continue
            dem.update(nguoi)

        moi = {p for p, n in dem.most_common(TARGET_PLAYERS) if n >= MIN_MATCHES}
        if moi == chon:
            break
        chon = moi
    return chon


def loc_tran(rows, tap_nguoi):
    ket_qua = []
    for r in rows:
        t1 = tach_nguoi_choi(r["team1"])
        t2 = tach_nguoi_choi(r["team2"])
        if not t1 or not t2:
            continue
        if not all(p in tap_nguoi for p in t1 + t2):
            continue
        games = doc_ty_so(r["score"])
        if games is None:
            BI_LOAI["ty so khong hop le hoac tran bo cuoc"] += 1
            continue
        ket_qua.append({"row": r, "t1": t1, "t2": t2, "games": games})
    ket_qua.sort(key=lambda m: m["row"]["date"])
    return ket_qua


DAU_CACH_KHONG_CHUAN = re.compile(r"[^\d]+")

# Dem ly do loai tran, in ra cuoi de biet du lieu nguon sach den dau.
BI_LOAI = Counter()


def doc_ty_so(score: str):
    """'21-15 17-21 21-16' -> [(21,15), (17,21), (21,16)]. Tra None neu khong hop le."""
    games = []
    for phan in score.split():
        so = DAU_CACH_KHONG_CHUAN.split(phan.strip())
        so = [s for s in so if s != ""]
        if len(so) != 2:
            return None
        a, b = int(so[0]), int(so[1])
        if not hop_le_mot_set(a, b):
            return None
        games.append((a, b))
    if not 2 <= len(games) <= 3:
        return None
    # Phai co mot doi thang dung 2 set. BWF ghi tran bo cuoc giua chung bang cac
    # set da danh xong ma khong danh dau gi (xem README cua bo du lieu), nen o day
    # se thay nhung tran 1-1 set roi dung - loai bo.
    t1 = sum(1 for a, b in games if a > b)
    if max(t1, len(games) - t1) != 2:
        return None
    return games


def hop_le_mot_set(a: int, b: int) -> bool:
    """Luat cau long: den 21, hon 2 diem, tran 30 (thiet-ke-sprint-2 muc 2.2.4)."""
    cao, thap = max(a, b), min(a, b)
    if cao == 21:
        return thap <= 19
    if 22 <= cao <= 30:
        return cao - thap == 2 or (cao == 30 and thap == 29)
    return False


def doi_thang(games):
    t1 = sum(1 for a, b in games if a > b)
    t2 = len(games) - t1
    return 1 if t1 > t2 else 2


def suy_gioi_tinh(tran_theo_nguoi):
    """MS/MD -> nam, WS/WD -> nu. Chi choi XD thi suy tu vi tri (BWF xep nam truoc)."""
    gioi_tinh = {}
    chua_ro = []
    for nguoi, ds in tran_theo_nguoi.items():
        phieu = Counter()
        for m in ds:
            g = DISCIPLINE_TO_GENDER.get(m["row"]["discipline"])
            if g:
                phieu[g] += 1
        if phieu:
            gioi_tinh[nguoi] = phieu.most_common(1)[0][0]
        else:
            chua_ro.append(nguoi)

    for nguoi in chua_ro:
        m = tran_theo_nguoi[nguoi][0]
        doi = m["t1"] if nguoi in m["t1"] else m["t2"]
        gioi_tinh[nguoi] = "male" if doi.index(nguoi) == 0 else "female"
    return gioi_tinh, chua_ro


def khoa_nguoi_choi(ten: str) -> str:
    bo_dau = unicodedata.normalize("NFKD", ten).encode("ascii", "ignore").decode()
    slug = re.sub(r"[^a-z0-9]+", "-", bo_dau.lower()).strip("-")
    return f"bwf-{slug}"


def email_tu_ten(ten: str, da_dung: set) -> str:
    bo_dau = unicodedata.normalize("NFKD", ten).encode("ascii", "ignore").decode()
    phan = [p for p in re.split(r"[^a-z0-9]+", bo_dau.lower()) if p]
    goc = ".".join(phan) or "player"
    # .example la TLD danh rieng cho tai lieu, khong bao gio gui thu that toi do.
    ung_vien = f"{goc}@bwf.example"
    i = 2
    while ung_vien in da_dung:
        ung_vien = f"{goc}{i}@bwf.example"
        i += 1
    da_dung.add(ung_vien)
    return ung_vien


def main():
    print(f"Doc {SOURCE.relative_to(ROOT)} ...")
    rows = list(doc_du_lieu())
    print(f"  vong chinh tu {FROM_DATE}: {len(rows):,} tran")

    tap_nguoi = chon_nguoi_choi(rows)
    print(f"  chon duoc {len(tap_nguoi)} van dong vien")

    tran = loc_tran(rows, tap_nguoi)
    print(f"  tran ma CA HAI doi deu nam trong tap: {len(tran):,}")
    for ly_do, n in BI_LOAI.items():
        print(f"  loai bo {n} tran: {ly_do}")
    BI_LOAI.clear()

    tran_theo_nguoi = defaultdict(list)
    for m in tran:
        for p in m["t1"] + m["t2"]:
            tran_theo_nguoi[p].append(m)

    # Bo nguoi con qua it tran sau khi loc
    bo = {p for p, ds in tran_theo_nguoi.items() if len(ds) < MIN_MATCHES}
    if bo:
        tap_nguoi -= bo
        tran = loc_tran(rows, tap_nguoi)
        tran_theo_nguoi = defaultdict(list)
        for m in tran:
            for p in m["t1"] + m["t2"]:
                tran_theo_nguoi[p].append(m)
        print(f"  sau khi bo {len(bo)} nguoi it tran: {len(tap_nguoi)} nguoi / {len(tran):,} tran")

    gioi_tinh, chua_ro = suy_gioi_tinh(tran_theo_nguoi)
    if chua_ro:
        print(f"  {len(chua_ro)} nguoi chi choi doi nam nu, gioi tinh suy tu vi tri")

    # --- Tinh Elo theo thu tu thoi gian --------------------------------------
    rating = {p: DIEM_KHOI_TAO for p in tap_nguoi}
    da_choi = Counter()
    thang = Counter()
    thua = Counter()
    tong_ghi = Counter()
    tong_thung = Counter()
    lan_cuoi = {}
    lich_su = []

    ds_tran_json = []
    for i, m in enumerate(tran, start=1):
        r = m["row"]
        t1, t2, games = m["t1"], m["t2"], m["games"]
        thang_doi = doi_thang(games)

        diem_t1 = sum(a for a, _ in games)
        diem_t2 = sum(b for _, b in games)

        r1 = sum(rating[p] for p in t1) / len(t1)
        r2 = sum(rating[p] for p in t2) / len(t2)
        ky_vong_1 = 1.0 / (1.0 + 10 ** ((r2 - r1) / 400.0))

        ngay = datetime.strptime(r["date"], "%Y-%m-%d").date()
        days_ago = (MOC_HOM_NAY - ngay).days
        match_key = f"bwf-m{i:05d}"

        for doi_no, doi, ky_vong in ((1, t1, ky_vong_1), (2, t2, 1.0 - ky_vong_1)):
            ket_qua = 1.0 if doi_no == thang_doi else 0.0
            for p in doi:
                cu = rating[p]
                k = he_so_k(da_choi[p])
                moi = max(SAN_DIEM, cu + k * (ket_qua - ky_vong))
                rating[p] = moi
                lich_su.append({
                    "playerKey": khoa_nguoi_choi(p),
                    "matchKey": match_key,
                    "oldRating": f"{cu:.2f}",
                    "newRating": f"{moi:.2f}",
                    "changeAmount": f"{moi - cu:.2f}",
                    "daysAgo": days_ago,
                })
                da_choi[p] += 1
                if doi_no == thang_doi:
                    thang[p] += 1
                else:
                    thua[p] += 1
                tong_ghi[p] += diem_t1 if doi_no == 1 else diem_t2
                tong_thung[p] += diem_t2 if doi_no == 1 else diem_t1
                lan_cuoi[p] = min(lan_cuoi.get(p, 10**9), days_ago)

        ds_tran_json.append({
            "key": match_key,
            "type": DISCIPLINE_TO_TYPE[r["discipline"]],
            "daysAgo": days_ago,
            "team1": [khoa_nguoi_choi(p) for p in t1],
            "team2": [khoa_nguoi_choi(p) for p in t2],
            "games": [[a, b] for a, b in games],
            "winningTeam": thang_doi,
            "note": f"{r['tournament']} - {r['round']}",
        })

    # --- Nguoi choi ----------------------------------------------------------
    da_dung_email = set()
    ds_nguoi_json = []
    for idx, p in enumerate(sorted(tap_nguoi), start=1):
        n = da_choi[p]
        loai = Counter(m["row"]["discipline"] for m in tran_theo_nguoi[p])
        chinh = loai.most_common(1)[0][0]
        ds_nguoi_json.append({
            "key": khoa_nguoi_choi(p),
            "fullName": p,
            "email": email_tu_ten(p, da_dung_email),
            "phone": f"097{idx:07d}",
            "gender": gioi_tinh[p],
            # Deu la van dong vien chuyen nghiep - day la su that, khong phai suy doan.
            "skillLevel": "professional",
            "preferredPlayType": {"single": "single", "double": "double",
                                  "mixed_double": "mixed"}[DISCIPLINE_TO_TYPE[chinh]],
            "statistics": {
                "totalMatches": n,
                "totalWins": thang[p],
                "totalLosses": thua[p],
                "winRate": f"{thang[p] / n * 100:.2f}",
                "ratingScore": f"{rating[p]:.2f}",
                "avgPointsScored": f"{tong_ghi[p] / n:.2f}",
                "avgPointsConceded": f"{tong_thung[p] / n:.2f}",
                "lastPlayedDaysAgo": lan_cuoi[p],
            },
        })

    meta = {
        "source": "BWF Badminton Match Results 2007-2026 (data/bwf-match-data)",
        "generatedBy": "data/bwf-to-seed.py",
        "filter": f"vong dau chinh, tu {FROM_DATE}",
        "referenceDate": MOC_HOM_NAY.isoformat(),
        "eloParams": {"initial": DIEM_KHOI_TAO, "floor": SAN_DIEM,
                      "k": "40 duoi 10 tran, 24 tu 10-29, 16 tu 30 tran"},
        "playerCount": len(ds_nguoi_json),
        "matchCount": len(ds_tran_json),
    }

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUT_DIR / "bwf-players.json").write_text(
        json.dumps({"meta": meta, "players": ds_nguoi_json}, ensure_ascii=False, indent=1),
        encoding="utf-8")
    (OUT_DIR / "bwf-matches.json").write_text(
        json.dumps({"meta": meta, "matches": ds_tran_json, "ratingHistory": lich_su},
                   ensure_ascii=False, indent=1),
        encoding="utf-8")

    # --- Bao cao -------------------------------------------------------------
    so_tran = sorted(da_choi.values())
    diem = sorted(rating.values())
    print()
    print(f"  Nguoi choi        : {len(ds_nguoi_json)}")
    print(f"  Tran dau          : {len(ds_tran_json):,}")
    print(f"  Dong lich su diem : {len(lich_su):,}")
    print(f"  Tran/nguoi        : it nhat {so_tran[0]}, trung vi {so_tran[len(so_tran)//2]}, nhieu nhat {so_tran[-1]}")
    print(f"  Diem Elo          : {diem[0]:.0f} - {diem[-1]:.0f}, trung vi {diem[len(diem)//2]:.0f}")
    for f in ("bwf-players.json", "bwf-matches.json"):
        print(f"  {f:20} {(OUT_DIR / f).stat().st_size / 1024:8.0f} KB")


if __name__ == "__main__":
    main()

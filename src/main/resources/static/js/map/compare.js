// 상권 비교 화면. 비교 대상은 ?ids= 또는 비교함에서 온다.

const COL_COLORS = ["#c7735b", "#8a5a4b", "#a9432e", "#5e6b52"];
const AGE_FIELDS = [["10대", "agrde10SelngAmt"], ["20대", "agrde20SelngAmt"], ["30대", "agrde30SelngAmt"],
    ["40대", "agrde40SelngAmt"], ["50대", "agrde50SelngAmt"], ["60+", "agrde60AboveSelngAmt"]];
const AGE_COLORS = ["#f0d6c9", "#e2ac96", "#d18a6f", "#bb6247", "#a8412c", "#7e2a1b"];

const cmp = { ids: [], picked: [], sales: new Map(), all: [], query: "" };

function chip(i) {
    return '<span style="display:inline-block;width:9px;height:9px;border-radius:50%;background:' + COL_COLORS[i] + ';margin-right:7px"></span>';
}

function card(title, unit, bodyHtml) {
    return '<section style="background:#fff;border:1px solid #eee2da;border-radius:14px;padding:18px 20px;min-height:170px">' +
        '<div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:14px">' +
        '<h2 style="font-size:15px;font-weight:700;color:#3f342e">' + title + "</h2>" +
        '<span style="font-size:12px;color:#9a8c84">' + unit + "</span></div>" + bodyHtml + "</section>";
}

// 지표 하나를 상권별 가로 막대로
function barCard(title, unit, valueOf, fmt) {
    const max = Math.max(...cmp.picked.map((d) => valueOf(d) || 0), 0);
    const rows = cmp.picked.map((d, i) => {
        const v = valueOf(d);
        const w = max ? Math.round(((v || 0) / max) * 100) : 0;
        const best = v != null && v === max && max > 0 && cmp.picked.length > 1;
        return '<div style="margin-bottom:10px">' +
            '<div style="display:flex;justify-content:space-between;font-size:13px;margin-bottom:3px">' +
            "<span>" + chip(i) + d.trdarNm + "</span>" +
            '<b style="color:' + (best ? "#a8412c" : "#3f342e") + '">' + (v != null ? fmt(v) : "-") + (best ? " ★" : "") + "</b></div>" +
            '<div style="height:7px;border-radius:4px;background:#f4ece6"><div style="height:7px;border-radius:4px;width:' + w + "%;background:" + COL_COLORS[i] + '"></div></div></div>';
    }).join("");
    return card(title, unit, rows);
}

// 연령·성별: 연령 6구간 누적 막대 + 남녀 비중
function ageCard() {
    const rows = cmp.picked.map((d, i) => {
        const s = cmp.sales.get(d.trdarCd);
        if (!s) {
            return '<div style="margin-bottom:12px;font-size:13px">' + chip(i) + d.trdarNm + ' <span style="color:#9a8c84">데이터 없음</span></div>';
        }
        const total = AGE_FIELDS.reduce((a, [, f]) => a + (s[f] || 0), 0);
        const segs = total ? AGE_FIELDS.map(([, f], k) =>
            '<div style="width:' + ((s[f] || 0) / total * 100).toFixed(1) + "%;background:" + AGE_COLORS[k] + '"></div>').join("") : "";
        const ml = (s.mlSelngAmt || 0);
        const fml = (s.fmlSelngAmt || 0);
        const mlPct = ml + fml ? Math.round(ml / (ml + fml) * 100) : null;
        return '<div style="margin-bottom:12px">' +
            '<div style="display:flex;justify-content:space-between;font-size:13px;margin-bottom:3px">' +
            "<span>" + chip(i) + d.trdarNm + "</span>" +
            "<span style='color:#5c5049'>" + (mlPct != null ? "남 " + mlPct + "% · 여 " + (100 - mlPct) + "%" : "-") + "</span></div>" +
            '<div style="display:flex;height:9px;border-radius:5px;overflow:hidden;background:#f4ece6">' + segs + "</div></div>";
    }).join("");
    const legend = '<div style="display:flex;gap:9px;flex-wrap:wrap;margin-top:6px">' +
        AGE_FIELDS.map(([nm], k) => '<span style="font-size:11px;color:#8c7f78">' +
            '<span style="display:inline-block;width:8px;height:8px;border-radius:2px;background:' + AGE_COLORS[k] + ';margin-right:3px"></span>' + nm + "</span>").join("") + "</div>";
    return card("고객 연령·성별", "최근 분기 매출 비중", rows + legend);
}

// 업종 구성: 상권별 매출 상위 3개 업종
function mixCard() {
    const rows = cmp.picked.map((d, i) => {
        const s = cmp.sales.get(d.trdarCd);
        let text = '<span style="color:#9a8c84">데이터 없음</span>';
        if (s && s.top && s.top.length) {
            text = s.top.map(([cd, pct]) => {
                const nm = (typeof INDUTY_NM !== "undefined" && INDUTY_NM[cd]) || cd;
                return nm + " " + pct + "%";
            }).join(" · ");
        }
        return '<div style="margin-bottom:11px;font-size:13px;line-height:1.5">' +
            "<div>" + chip(i) + "<b>" + d.trdarNm + "</b></div>" +
            '<div style="color:#5c5049;margin-left:16px">' + text + "</div></div>";
    }).join("");
    return card("업종 구성 TOP3", "최근 분기 매출 비중", rows);
}

// 상권 검색으로 비교함에 바로 담는다
function searchBox() {
    const full = cmp.picked.length >= 4;
    return '<div style="position:relative;max-width:340px;margin-bottom:14px">' +
        '<input type="search" id="cmp-search" placeholder="' + (full ? "최대 4개까지 비교할 수 있습니다" : "상권 이름으로 추가") + '" ' +
        (full ? "disabled " : "") + 'value="' + cmp.query + '" ' +
        'style="width:100%;padding:9px 13px;border:1px solid #e2d6cf;border-radius:10px;font:inherit;background:#fff">' +
        '<div id="cmp-suggest" style="position:absolute;top:100%;left:0;right:0;z-index:10;background:#fff;' +
        'border:1px solid #e2d6cf;border-radius:10px;margin-top:4px;box-shadow:0 6px 18px rgba(94,30,17,.12);display:none"></div></div>';
}

function bindSearch() {
    const input = document.getElementById("cmp-search");
    const box = document.getElementById("cmp-suggest");
    if (!input || input.disabled) {
        return;
    }
    let timer = null;
    input.addEventListener("input", () => {
        clearTimeout(timer);
        timer = setTimeout(() => {
            cmp.query = input.value.trim();
            if (!cmp.query) {
                box.style.display = "none";
                return;
            }
            const hits = cmp.all
                .filter((d) => !cmp.ids.includes(d.trdarCd))
                .filter((d) => d.trdarNm.includes(cmp.query) || (d.signguNm || "").includes(cmp.query))
                .slice(0, 8);
            box.innerHTML = hits.length
                ? hits.map((d) =>
                    '<div class="cmp-hit" data-cd="' + d.trdarCd + '" style="padding:9px 13px;cursor:pointer;font-size:13px;display:flex;justify-content:space-between">' +
                    "<span>" + d.trdarNm + '</span><span style="color:#9a8c84">' + (d.signguNm || "") + "</span></div>").join("")
                : '<div style="padding:9px 13px;font-size:13px;color:#9a8c84">일치하는 상권이 없습니다</div>';
            box.style.display = "";
            box.querySelectorAll(".cmp-hit").forEach((row) => {
                row.addEventListener("click", () => addDistrict(row.dataset.cd));
            });
        }, 250);
    });
}

async function addDistrict(trdarCd) {
    const d = cmp.all.find((x) => x.trdarCd === trdarCd);
    if (!d || cmp.ids.includes(trdarCd) || cmp.picked.length >= 4) {
        return;
    }
    cmp.ids.push(trdarCd);
    cmp.picked.push(d);
    cmpSave(cmp.ids);
    cmp.query = "";
    render();
    try {
        cmp.sales.set(trdarCd, digestSales(await apiData("/api/sales?trdarCd=" + trdarCd)));
        render();
    } catch (e) { /* 연령·업종만 비면 데이터 없음으로 남는다 */ }
}

function render() {
    const main = document.querySelector("main.compare");
    if (!cmp.picked.length) {
        main.innerHTML =
            '<div style="margin:80px auto;max-width:420px;text-align:center;color:#8c7f78">' +
            '<p style="font-size:17px;font-weight:600;margin-bottom:8px">비교함이 비어 있습니다</p>' +
            '<p style="font-size:14px;line-height:1.6;margin-bottom:16px">아래에서 상권을 검색해 담거나,<br>지도·상권 상세에서 비교 담기를 누르세요.</p>' +
            searchBox() + "</div>";
        bindSearch();
        return;
    }

    const chips = cmp.picked.map((d, i) =>
        '<span style="display:inline-flex;align-items:center;gap:6px;padding:6px 8px 6px 12px;border:1px solid #eee2da;border-radius:18px;background:#fff;font-size:13px">' +
        chip(i) + d.trdarNm + ' <span style="color:#9a8c84">' + (d.signguNm || "") + "</span>" +
        '<button type="button" class="chip-remove" data-cd="' + d.trdarCd + '" aria-label="' + d.trdarNm + ' 제거" ' +
        'style="border:0;background:none;color:#b0857a;cursor:pointer;font-size:13px">✕</button></span>'
    ).join(" ");

    main.innerHTML =
        '<div style="max-width:1060px;margin:22px auto;padding:0 20px">' +
        searchBox() +
        '<div style="display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px">' + chips + "</div>" +
        '<div class="cmp-grid" style="display:grid;grid-template-columns:1fr 1fr;gap:14px">' +
        barCard("추정 매출", "억/분기", (d) => d.salesAmt, (v) => fmtEok(v)) +
        barCard("유동인구", "만 명", (d) => d.flpop, (v) => fmtMan(v)) +
        ageCard() +
        mixCard() +
        "</div></div>";

    main.querySelectorAll(".chip-remove").forEach((btn) => {
        btn.addEventListener("click", () => {
            cmp.ids = cmp.ids.filter((id) => id !== btn.dataset.cd);
            cmp.picked = cmp.picked.filter((d) => d.trdarCd !== btn.dataset.cd);
            cmpSave(cmp.ids);
            render();
        });
    });
    bindSearch();
}

// 상권별 최근 분기 매출을 합쳐 연령·성별·업종 비중을 만든다
function digestSales(rows) {
    if (!rows || !rows.length) {
        return null;
    }
    const q = latestQuarter(rows);
    const mine = rows.filter((r) => r.stdrYyquCd === q);
    const sum = (f) => mine.reduce((a, r) => a + (r[f] || 0), 0);
    const out = { mlSelngAmt: sum("mlSelngAmt"), fmlSelngAmt: sum("fmlSelngAmt") };
    AGE_FIELDS.forEach(([, f]) => { out[f] = sum(f); });
    const byInduty = new Map();
    let total = 0;
    mine.forEach((r) => {
        byInduty.set(r.indutyCd, (byInduty.get(r.indutyCd) || 0) + (r.thsmonSelngAmt || 0));
        total += r.thsmonSelngAmt || 0;
    });
    out.top = [...byInduty.entries()].sort((a, b) => b[1] - a[1]).slice(0, 3)
        .map(([cd, amt]) => [cd, total ? Math.round(amt / total * 100) : 0]);
    return out;
}

async function load() {
    const q = new URLSearchParams(location.search).get("ids");
    cmp.ids = (q ? q.split(",").filter(Boolean) : cmpList()).slice(0, 4);

    const all = (await apiData("/api/districts/summary")) || [];
    cmp.all = all;
    const byId = new Map(all.map((d) => [d.trdarCd, d]));
    cmp.picked = cmp.ids.map((id) => byId.get(id)).filter(Boolean);

    const quarterBtn = document.querySelector(".app-quarter");
    if (quarterBtn && all.length) {
        quarterBtn.textContent = quarterLabel(all[0].quarter);
    }
    render(); // 매출·유동 먼저 그린다

    // 연령·성별·업종은 상권별 매출을 받아 채운다
    const results = await Promise.allSettled(
        cmp.picked.map((d) => apiData("/api/sales?trdarCd=" + d.trdarCd)));
    results.forEach((r, i) => {
        if (r.status === "fulfilled") {
            cmp.sales.set(cmp.picked[i].trdarCd, digestSales(r.value));
        }
    });
    render();
}

load().catch((err) => {
    console.error("비교 로드 실패:", err);
    document.querySelector("main.compare").innerHTML =
        '<p style="margin:80px auto;text-align:center;color:#8c7f78">비교 데이터를 불러오지 못했습니다. 서버 연결을 확인해 주세요.</p>';
});

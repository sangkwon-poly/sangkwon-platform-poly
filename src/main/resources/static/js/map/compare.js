// 상권 비교 화면. 비교 대상은 ?ids= 또는 비교함에서 온다.

const COL_COLORS = ["#c7735b", "#b85942", "#a9432e", "#913423"];
const cmp = { ids: [], byId: new Map() };

function metricRow(name, unit, picked, valueOf, fmt) {
    const values = picked.map(valueOf);
    const max = Math.max(...values.map((v) => v || 0), 0);
    const cells = values.map((v) => {
        const width = max ? Math.round(((v || 0) / max) * 100) : 0;
        const best = v != null && v === max && max > 0 && picked.length > 1;
        return '<td class="cmp-cell' + (best ? " is-best" : "") + '">' +
            '<span class="cmp-val">' + (v != null ? fmt(v) : "-") + "</span>" +
            (best ? '<span class="cmp-tag">최고</span>' : "") +
            '<span class="cmp-bar"><span style="width:' + width + '%;background:#b85942"></span></span></td>';
    }).join("");
    return '<tr><th class="cmp-metric" scope="row"><span class="cmp-metric-name">' + name +
        '</span><span class="cmp-metric-unit">' + unit + "</span></th>" + cells + "</tr>";
}

function render() {
    const main = document.querySelector("main.compare");
    const picked = cmp.ids.map((id) => cmp.byId.get(id)).filter(Boolean).slice(0, 4);

    if (!picked.length) {
        main.innerHTML =
            '<div style="margin:80px auto;max-width:420px;text-align:center;color:#8c7f78">' +
            '<p style="font-size:17px;font-weight:600;margin-bottom:8px">비교함이 비어 있습니다</p>' +
            '<p style="font-size:14px;line-height:1.6">검색 결과나 상권 상세에서 비교 담기를 누르면<br>최대 4개까지 나란히 볼 수 있습니다.</p>' +
            '<p style="margin-top:16px"><a href="/map/search.html" style="color:#a8412c;font-weight:600">상권 검색으로 이동 →</a></p></div>';
        return;
    }

    const head = '<tr><th class="cmp-metric-h" scope="col">지표</th>' +
        picked.map((d, i) =>
            '<th class="cmp-col" scope="col">' +
            '<button type="button" class="cmp-remove" data-cd="' + d.trdarCd + '" aria-label="' + d.trdarNm + ' 제거">✕</button>' +
            '<span class="cmp-color" style="background:' + COL_COLORS[i] + '"></span>' +
            '<span class="cmp-place">' + d.trdarNm + '</span><span class="cmp-place-sub">' + (d.signguNm || "") + "</span></th>"
        ).join("") + "</tr>";

    const body =
        metricRow("추정 매출", "억/분기", picked, (d) => d.salesAmt, (v) => fmtEok(v)) +
        metricRow("유동인구", "만 명", picked, (d) => d.flpop, (v) => fmtMan(v)) +
        metricRow("점포 수", "개", picked, (d) => d.storeCnt, (v) => v.toLocaleString()) +
        '<tr><th class="cmp-metric" scope="row"><span class="cmp-metric-name">변화지표</span></th>' +
        picked.map((d) => '<td class="cmp-cell"><span class="cmp-val">' + (d.changeIxNm || "-") + "</span></td>").join("") + "</tr>";

    const table = document.querySelector("table.cmp");
    table.querySelector("thead").innerHTML = head;
    table.querySelector("tbody").innerHTML = body;

    table.querySelectorAll(".cmp-remove").forEach((btn) => {
        btn.addEventListener("click", () => {
            cmp.ids = cmp.ids.filter((id) => id !== btn.dataset.cd);
            cmpSave(cmp.ids);
            render();
        });
    });
}

async function load() {
    const q = new URLSearchParams(location.search).get("ids");
    cmp.ids = q ? q.split(",").filter(Boolean) : cmpList();

    const all = (await apiData("/api/districts/summary")) || [];
    cmp.byId = new Map(all.map((d) => [d.trdarCd, d]));

    const quarterBtn = document.querySelector(".app-quarter");
    if (quarterBtn && all.length) {
        quarterBtn.textContent = quarterLabel(all[0].quarter);
    }
    render();
}

load().catch((err) => {
    console.error("비교 로드 실패:", err);
    document.querySelector("main.compare").innerHTML =
        '<p style="margin:80px auto;text-align:center;color:#8c7f78">비교 데이터를 불러오지 못했습니다. 서버 연결을 확인해 주세요.</p>';
});

import Link from "next/link";
import { Logo, LogoMark } from "@/components/brand/logo";
import { ThemeToggle } from "@/components/theme/theme-toggle";

const chatPreview = [
  { who: "민호", body: "애월 독채 거실 넓어서 좋을 듯 ㅋㅋ" },
  { who: "하은", body: "사진 보내줘 ㅋㅋㅋㅋ" },
  { who: "서연", body: "차 없으면 애월 불편하지 않아?" },
  { who: "지수", body: "그럼 14일 금요일 출발로 하자", highlight: true },
  { who: "도윤", body: "비행기 몇 시꺼 끊어?" },
];

const summaryPreview = [
  { title: "날짜 11.14 – 16", chip: "거의 정해짐", chipClass: "chip-confirmed", note: "“그럼 14일 금요일 출발로 하자” — 지수" },
  { title: "장소 애월 vs 서귀포", chip: "의견 갈림", chipClass: "chip-voting", note: "찬성 4 : 3 · 투표로 정해요" },
  { title: "공항에 몇 시에 모여?", chip: "답 없음", chipClass: "chip-discussion", note: "3일째 아무도 답하지 않았어요" },
];

const steps = [
  {
    title: "대화를 가져와요",
    body: "카톡 ‘대화 내보내기’ 파일을 올리거나 텍스트를 붙여 넣어요. 고른 기간만, 전화번호·계좌번호를 가린 뒤 보내요.",
  },
  {
    title: "정리된 걸 확인해요",
    body: "거의 정해진 것, 의견이 갈린 것, 답이 없는 질문, 할 일로 나눠요. 항목마다 누가 언제 한 말인지 근거가 붙어요.",
  },
  {
    title: "링크로 끝까지 정해요",
    body: "단톡방에 링크를 다시 올리면 남은 항목에 다 같이 투표해요. 확정은 호스트가 직접 눌러요.",
  },
];

const privacyPoints = [
  { title: "원본은 기기에서만", body: "파일은 브라우저에서 읽고, 고른 기간만 보내요." },
  { title: "민감한 정보는 가림", body: "전화번호·계좌번호·주민번호·이메일을 보내기 전에 가려요." },
  { title: "AI에는 가명으로", body: "이름 대신 ‘참여자 A, B’로 보내고, 학습에는 쓰지 않아요." },
  { title: "언제든 지울 수 있음", body: "모임으로 만들지 않으면 7일 뒤 자동으로 지워져요." },
];

// Availability grid for the date feature card: 1 = available, 0 = no, 2 = maybe.
const dateRows = [
  { label: "11.07", cells: [1, 0, 1, 1, 0, 1] },
  { label: "11.14", cells: [1, 1, 1, 2, 1, 1], best: true },
  { label: "11.21", cells: [1, 1, 0, 1, 2, 0] },
];

function cellClass(value: number) {
  if (value === 1) return "bg-ink";
  if (value === 2) return "bg-[#F2B33D]";
  return "border border-line";
}

const wrap = "mx-auto w-full max-w-[1200px] px-4 sm:px-8";
const bigButton =
  "inline-flex min-h-[52px] items-center justify-center gap-2 rounded-[14px] px-[22px] text-base font-bold transition hover:opacity-90";

export function LandingPage() {
  return (
    <div className="min-h-screen bg-bg text-ink">
      <header>
        <div className={`${wrap} flex flex-wrap items-center justify-between gap-4 py-[18px]`}>
          <Link href="/" aria-label="dudecide 홈" className="flex min-h-[44px] items-center">
            <Logo size={30} textClassName="text-[22px]" />
          </Link>
          <nav aria-label="주요 메뉴" className="hidden gap-7 sm:flex">
            {[
              { href: "#how", label: "어떻게 쓰나요" },
              { href: "#features", label: "기능" },
              { href: "#privacy", label: "개인정보 보호" },
            ].map((item) => (
              <a
                key={item.href}
                href={item.href}
                className="inline-flex min-h-[44px] items-center text-[15px] font-semibold text-ink/80 hover:text-ink"
              >
                {item.label}
              </a>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <Link
              href="/start"
              className="inline-flex min-h-[44px] items-center px-3 text-[15px] font-semibold text-ink/80 hover:text-ink"
            >
              시작하기
            </Link>
            <Link href="/meetings/new" className="action-primary">
              모임 만들기
            </Link>
          </div>
        </div>
      </header>

      <section aria-label="소개" className={`${wrap} flex flex-wrap items-center gap-14 pb-[88px] pt-14`}>
        <div className="flex min-w-0 flex-[1_1_440px] flex-col gap-6">
          <span className="mono-label !text-accent-text">단톡방 대화 → 결정 정리</span>
          <h1 className="font-display m-0 text-[clamp(2.6rem,5.4vw,4.4rem)] leading-[1.08]">
            수다는 그대로,
            <br />
            결정은 <span className="text-accent-text">또렷하게</span>.
          </h1>
          <p className="m-0 max-w-[34rem] text-lg leading-[1.65] text-ink/80">
            단톡방 대화를 붙여 넣으면 정해진 것, 아직 남은 것, 누가 뭘 하는지를{" "}
            <b className="text-ink">근거 메시지와 함께</b> 정리해요. 남은 건 링크 하나로 다 같이
            투표해서 끝내요.
          </p>
          <div className="flex flex-wrap gap-2.5">
            <Link href="/import" className={`${bigButton} bg-accent text-on-accent`}>
              <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="8" y="3" width="8" height="4" rx="1" />
                <path d="M8 5H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2" />
              </svg>
              대화 붙여넣고 시작하기
            </Link>
            <Link href="/meetings/new" className={`${bigButton} border border-line bg-surface text-ink`}>
              새 모임 만들기
            </Link>
          </div>
          <div className="flex flex-wrap gap-[18px] text-[13px] text-muted">
            <span>✓ 가입 없이 바로</span>
            <span>✓ 원본 파일은 내 기기에서만</span>
            <span>✓ AI는 제안만, 확정은 사람이</span>
          </div>
        </div>

        <div aria-label="정리 전후 예시" className="flex min-w-0 flex-[1_1_520px] flex-wrap items-stretch gap-3.5">
          <div className="flex flex-[1_1_230px] flex-col gap-2.5 rounded-3xl bg-surface p-5">
            <div className="flex items-center justify-between">
              <span className="text-[13px] font-bold">제주 가즈아 (8)</span>
              <span className="num text-[11px] text-muted">1,830개</span>
            </div>
            {chatPreview.map((message) => (
              <div key={message.who + message.body}>
                <div className="mb-[3px] text-[11px] font-bold text-muted">{message.who}</div>
                <div
                  className={`max-w-[85%] rounded-[4px_14px_14px_14px] px-3 py-2 text-[13px] leading-[1.45] ${
                    message.highlight ? "bg-accent-soft text-ink" : "bg-surface-2 text-ink/80"
                  }`}
                >
                  {message.body}
                </div>
              </div>
            ))}
            <div className="num text-center text-[11px] text-muted">· · · 위로 1,825개 더</div>
          </div>

          <div className="flex flex-[1.2_1_260px] flex-col gap-2.5 rounded-3xl bg-surface p-5 ring-2 ring-accent">
            <div className="flex items-center gap-1.5">
              <LogoMark size={18} />
              <span className="text-[13px] font-bold">정리 결과 · 40초</span>
            </div>
            <div className="flex flex-col gap-2">
              {summaryPreview.map((item) => (
                <div key={item.title} className="flex flex-col gap-1 rounded-[14px] bg-surface-2 p-3">
                  <div className="flex justify-between gap-2">
                    <b className="text-sm">{item.title}</b>
                    <span className={item.chipClass}>{item.chip}</span>
                  </div>
                  <span className="text-xs text-muted">{item.note}</span>
                </div>
              ))}
              <div className="flex items-center justify-between gap-2 rounded-[14px] bg-surface-2 p-3">
                <b className="text-sm">항공권 알아보기</b>
                <span className="num text-[11px] text-muted">할 일 · 서연</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="how" aria-label="어떻게 쓰나요" className="bg-surface py-[88px]">
        <div className={`${wrap} flex flex-col gap-10`}>
          <div className="flex max-w-[40rem] flex-col gap-3">
            <span className="mono-label !text-accent-text">HOW IT WORKS</span>
            <h2 className="font-display m-0 text-[clamp(2rem,3.6vw,2.8rem)] leading-[1.15]">
              단톡방은 그대로 두고,
              <br />
              정리만 여기서 해요
            </h2>
          </div>
          <ol className="m-0 grid list-none grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-5 p-0">
            {steps.map((step, index) => {
              const last = index === steps.length - 1;
              return (
                <li
                  key={step.title}
                  className={`flex flex-col gap-3 rounded-3xl p-7 ${last ? "bg-accent text-on-accent" : "bg-surface-2"}`}
                >
                  <span
                    className={`num flex h-9 w-9 items-center justify-center rounded-full font-semibold ${
                      last ? "bg-white text-[#2D46F5]" : "bg-ink text-on-ink"
                    }`}
                  >
                    {index + 1}
                  </span>
                  <h3 className="m-0 text-xl font-bold">{step.title}</h3>
                  <p className={`m-0 text-[15px] leading-[1.6] ${last ? "" : "text-ink/80"}`}>{step.body}</p>
                </li>
              );
            })}
          </ol>
        </div>
      </section>

      <section id="features" aria-label="기능" className={`${wrap} flex flex-col gap-10 py-[88px]`}>
        <div className="flex max-w-[40rem] flex-col gap-3">
          <span className="mono-label !text-accent-text">FEATURES</span>
          <h2 className="font-display m-0 text-[clamp(2rem,3.6vw,2.8rem)] leading-[1.15]">
            투표는 줄이고,
            <br />
            결정은 빨리
          </h2>
        </div>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(300px,1fr))] gap-5">
          <article className="flex flex-col gap-[18px] rounded-3xl bg-surface p-7">
            <div aria-hidden="true" className="grid grid-cols-[64px_repeat(6,minmax(0,1fr))] items-center gap-1 rounded-2xl bg-surface-2 p-4">
              {dateRows.map((row) => (
                <div key={row.label} className="contents">
                  <span className={`num text-[11px] font-bold ${row.best ? "text-accent-text" : "text-muted"}`}>{row.label}</span>
                  {row.cells.map((value, index) => (
                    <span key={index} className={`h-[22px] rounded-md ${cellClass(value)}`} />
                  ))}
                </div>
              ))}
            </div>
            <div>
              <h3 className="m-0 mb-1.5 text-xl font-bold">날짜는 표 하나로</h3>
              <p className="m-0 text-[15px] leading-[1.6] text-ink/80">
                가능·애매·불가만 표시하면 불가가 적은 날부터 추천해요. 남은 사람이 답해도 결과가 같으면 바로 마감할 수 있어요.
              </p>
            </div>
          </article>

          <article className="flex flex-col gap-[18px] rounded-3xl bg-surface p-7">
            <div aria-hidden="true" className="flex flex-col gap-2 rounded-2xl bg-surface-2 p-4">
              {[
                { key: "A", label: "애월 · 카페 & 해변", best: true },
                { key: "B", label: "서귀포 · 올레길 & 해산물" },
                { key: "C", label: "성산 · 일출봉 & 승마" },
              ].map((option) => (
                <div
                  key={option.key}
                  className={`flex items-center gap-2 rounded-[10px] bg-surface px-2.5 py-2 ${option.best ? "ring-2 ring-accent" : ""}`}
                >
                  <b className="font-display text-sm">{option.key}</b>
                  <span className="flex-1 text-xs font-semibold">{option.label}</span>
                  {option.best ? (
                    <span className="num rounded-full bg-accent px-2 py-1 text-[11px] font-semibold text-on-accent">반대 최소</span>
                  ) : null}
                </div>
              ))}
            </div>
            <div>
              <h3 className="m-0 mb-1.5 text-xl font-bold">장소는 3개만 비교</h3>
              <p className="m-0 text-[15px] leading-[1.6] text-ink/80">
                모두의 조건으로 후보를 3개까지 줄이고, 좋아·괜찮아·별로로 답해요. 1등보다 반대가 가장 적은 안을 추천해요.
              </p>
            </div>
          </article>

          <article className="flex flex-col gap-[18px] rounded-3xl bg-surface p-7">
            <div aria-hidden="true" className="flex flex-col gap-2 rounded-2xl bg-surface-2 p-4">
              <div className="flex justify-between">
                <b className="text-[13px]">숙소 · 애월 독채</b>
                <span className="chip-voting">확인 필요</span>
              </div>
              <div className="rounded-[10px] bg-surface px-2.5 py-2 text-xs leading-[1.6] text-ink/80">
                <span className="num text-muted">20:46</span> <b>지수</b> 숙소는 애월 독채로 가자
                <br />
                <span className="num text-muted">20:52</span> <b>하은</b> 찬성! 6만원대면 괜찮아
              </div>
              <div className="rounded-[10px] bg-risk-soft px-2.5 py-2 text-xs text-risk-ink">
                <b>반대 1</b> 서연: 차 없으면 불편하지 않아?
              </div>
            </div>
            <div>
              <h3 className="m-0 mb-1.5 text-xl font-bold">결정마다 근거가 남아요</h3>
              <p className="m-0 text-[15px] leading-[1.6] text-ink/80">
                왜 이렇게 정했는지 원래 메시지로 바로 돌아가요. 반대 의견도 지우지 않고 함께 남겨요.
              </p>
            </div>
          </article>
        </div>
      </section>

      <section id="privacy" aria-label="개인정보 보호" className="bg-band py-[88px] text-white">
        <div className={`${wrap} flex flex-wrap items-start gap-12`}>
          <div className="flex flex-[1_1_320px] flex-col gap-3.5">
            <span className="mono-label !text-[#9AA3FF]">PRIVACY</span>
            <h2 className="font-display m-0 text-[clamp(2rem,3.6vw,2.8rem)] leading-[1.15]">
              친구들의 대화라서
              <br />
              더 조심해요
            </h2>
            <p className="m-0 text-base leading-[1.65] text-[#C9CCD3]">
              올리는 사람은 한 명이지만, 대화에는 모두의 말이 들어 있어요. 그래서 처음부터 이렇게 만들었어요.
            </p>
          </div>
          <ul className="m-0 grid flex-[1.4_1_420px] list-none grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-3.5 p-0">
            {privacyPoints.map((point) => (
              <li key={point.title} className="flex flex-col gap-2 rounded-[18px] bg-band-card p-[22px]">
                <b className="text-base">{point.title}</b>
                <span className="text-sm leading-[1.55] text-[#C9CCD3]">{point.body}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section aria-label="시작하기" className={`${wrap} py-[88px]`}>
        <div className="flex flex-wrap items-center justify-between gap-7 rounded-[32px] bg-accent p-[clamp(32px,6vw,64px)] text-on-accent">
          <h2 className="font-display m-0 flex-[1_1_420px] text-[clamp(1.8rem,3.4vw,2.6rem)] leading-[1.2]">
            다음 모임은 단톡방에서 헤매지 말고,
            <br />
            정리해서 정해요.
          </h2>
          <Link href="/import" className={`${bigButton} bg-white text-[#2D46F5]`}>
            지금 대화 붙여넣기 →
          </Link>
        </div>
      </section>

      <footer className="border-t border-line">
        <div className={`${wrap} flex flex-wrap items-center justify-between gap-4 pb-9 pt-7 text-[13px] text-muted`}>
          <span className="flex items-center gap-1.5">
            <LogoMark size={18} />
            <b className="text-ink">dudecide</b>
          </span>
          <nav aria-label="하단 메뉴" className="flex flex-wrap gap-5">
            <a href="#privacy" className="inline-flex min-h-[44px] items-center hover:text-ink">
              개인정보 보호
            </a>
            <a href="#how" className="inline-flex min-h-[44px] items-center hover:text-ink">
              어떻게 쓰나요
            </a>
          </nav>
        </div>
      </footer>
    </div>
  );
}

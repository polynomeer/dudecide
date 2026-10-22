const STEPS = ["파일 고르기", "범위 · 참여자 · 가림 확인", "결과 검토"];

/** Three-step progress used across the import wizard and review. `current` is 1-based. */
export function ImportSteps({ current }: { current: 1 | 2 | 3 }) {
  return (
    <ol aria-label="진행 단계" className="m-0 flex list-none flex-wrap gap-[18px] p-0">
      {STEPS.map((label, index) => {
        const number = index + 1;
        const done = number < current;
        const active = number === current;
        return (
          <li
            key={label}
            aria-current={active ? "step" : undefined}
            className={`flex items-center gap-2 text-[13px] font-semibold ${active ? "text-ink" : "text-muted"}`}
          >
            <span
              className={`num flex h-[22px] w-[22px] items-center justify-center rounded-full text-[11px] ${
                done ? "bg-ink text-on-ink" : active ? "bg-accent text-on-accent" : "border-[1.5px] border-line"
              }`}
            >
              {done ? "✓" : number}
            </span>
            {label}
          </li>
        );
      })}
    </ol>
  );
}

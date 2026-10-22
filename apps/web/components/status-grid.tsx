const cards = [
  {
    title: "Fast entry",
    body: "Guest sessions and invite joins bring people into a planning room without onboarding friction.",
  },
  {
    title: "Structured discussion",
    body: "Threads isolate noisy topics so dates, lodging, and transport stop colliding in one endless scroll.",
  },
  {
    title: "Evidence-backed wrap-up",
    body: "Votes, decisions, search, and reports turn scattered opinions into an auditable summary.",
  },
];

export function StatusGrid() {
  return (
    <section className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr_1fr]">
      {cards.map((card, index) => (
        <article
          key={card.title}
          className={[
            "rounded-[2rem] border p-5 shadow-[var(--shadow-soft)] backdrop-blur-xl",
            index === 1
              ? "border-[rgba(36,80,255,0.2)] bg-[rgba(36,80,255,0.08)]"
              : "border-line bg-surface",
          ].join(" ")}
        >
          {index === 0 ? <div className="eyebrow">What changed</div> : null}
          <h3 className="mt-2 font-display text-[2rem]">
            {card.title}
          </h3>
          <p className="mt-3 max-w-sm text-sm leading-6 text-ink/70">
            {card.body}
          </p>
        </article>
      ))}
    </section>
  );
}

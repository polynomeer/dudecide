const cards = [
  {
    title: "Session Entry",
    body: "Guest session creation, current actor bootstrap, and sign-out are connected to backend auth endpoints.",
  },
  {
    title: "Meeting Setup",
    body: "Create-meeting route is scaffolded and ready to bind to the meeting creation API and invite generation flows.",
  },
  {
    title: "Workspace Surfaces",
    body: "Thread, vote, decision, search, and report routes are documented and can now be implemented against existing APIs.",
  },
];

export function StatusGrid() {
  return (
    <section className="grid gap-4 lg:grid-cols-3">
      {cards.map((card) => (
        <article key={card.title} className="panel px-5 py-5">
          <div className="eyebrow">Phase 1</div>
          <h3 className="mt-2 font-[var(--font-display)] text-2xl font-semibold tracking-[-0.04em]">
            {card.title}
          </h3>
          <p className="mt-3 text-sm leading-6 text-ink/70">{card.body}</p>
        </article>
      ))}
    </section>
  );
}

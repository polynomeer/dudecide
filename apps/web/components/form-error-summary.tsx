"use client";

type FormErrorSummaryProps = {
  title?: string;
  errors: Array<{ fieldId: string; message: string }>;
};

export function FormErrorSummary({
  title = "Please fix the following",
  errors,
}: FormErrorSummaryProps) {
  if (errors.length === 0) {
    return null;
  }

  return (
    <div
      role="alert"
      tabIndex={-1}
      className="rounded-[1.5rem] border border-[rgba(220,38,38,0.18)] bg-[rgba(220,38,38,0.06)] px-5 py-4 text-sm text-ink"
    >
      <h2 className="font-semibold text-ember">{title}</h2>
      <ul className="mt-3 space-y-2">
        {errors.map((error) => (
          <li key={error.fieldId}>
            <a href={`#${error.fieldId}`} className="underline underline-offset-2">
              {error.message}
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}

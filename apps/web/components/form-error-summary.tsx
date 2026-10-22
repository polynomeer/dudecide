"use client";

type FormErrorSummaryProps = {
  title?: string;
  errors: Array<{ fieldId: string; message: string }>;
};

export function FormErrorSummary({
  title = "확인이 필요해요",
  errors,
}: FormErrorSummaryProps) {
  if (errors.length === 0) {
    return null;
  }

  return (
    <div
      role="alert"
      tabIndex={-1}
      className="rounded-xl bg-risk-soft px-5 py-4 text-sm text-risk-ink"
    >
      <h2 className="m-0 text-sm font-bold">{title}</h2>
      <ul className="m-0 mt-2 space-y-1 pl-4">
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

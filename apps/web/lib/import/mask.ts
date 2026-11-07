export type MaskToken = "email" | "rrn" | "phone" | "card" | "account";

export type MaskResult = {
  text: string;
  counts: Record<MaskToken, number>;
  total: number;
};

const EMAIL = /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/g;
const RRN = /(?<!\d)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])-?[1-4]\d{6}(?!\d)/g;
const PHONES = [
  /\+82[-. ]?(?:\(0\))?1[016789][-. ]?\d{3,4}[-. ]?\d{4}(?!\d)/g,
  /(?<!\d)01[016789][-. ]?\d{3,4}[-. ]?\d{4}(?!\d)/g,
  /(?<!\d)0(?:2|[3-6][1-5]|70)[-. ]?\d{3,4}[-. ]?\d{4}(?!\d)/g,
];
const CARD_CANDIDATE = /(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)/g;
const HYPHENATED_DIGITS = /(?<!\d)\d{2,6}(?:-\d{2,6}){1,4}(?!\d)/g;
const LONG_DIGIT_RUN = /(?<!\d)\d(?:[ -]?\d){9,}(?!\d)/g;
const ACCOUNT_CONTEXT =
  /은행|계좌|입금|송금|이체|카뱅|카카오뱅크|케이뱅크|토스|국민|신한|우리|하나|농협|기업|새마을|우체국|account|bank/i;

/**
 * Replaces sensitive numbers and addresses with typed tokens before upload (ADR 0005). It errs toward
 * over-masking: a long digit run in a message that mentions a bank or account is always masked.
 */
export function maskSensitive(input: string): MaskResult {
  const counts: Record<MaskToken, number> = { email: 0, rrn: 0, phone: 0, card: 0, account: 0 };
  const replace = (text: string, pattern: RegExp, token: MaskToken, accept: (match: string) => boolean = () => true) =>
    text.replace(pattern, (match) => {
      if (!accept(match)) {
        return match;
      }
      counts[token]++;
      return `[${token}]`;
    });

  let text = replace(input, EMAIL, "email");
  text = replace(text, RRN, "rrn");
  for (const pattern of PHONES) {
    text = replace(text, pattern, "phone");
  }
  text = replace(text, CARD_CANDIDATE, "card", (match) => luhnValid(digitsOf(match)));
  text = replace(text, HYPHENATED_DIGITS, "account", (match) => {
    const digits = digitsOf(match).length;
    return digits >= 10 && digits <= 14;
  });
  if (ACCOUNT_CONTEXT.test(text)) {
    text = replace(text, LONG_DIGIT_RUN, "account");
  }

  const total = Object.values(counts).reduce((sum, count) => sum + count, 0);
  return { text, counts, total };
}

function digitsOf(value: string): string {
  return value.replace(/\D/g, "");
}

function luhnValid(digits: string): boolean {
  if (digits.length < 13 || digits.length > 19) {
    return false;
  }
  let sum = 0;
  for (let index = 0; index < digits.length; index++) {
    let digit = Number(digits[digits.length - 1 - index]);
    if (index % 2 === 1) {
      digit *= 2;
      if (digit > 9) {
        digit -= 9;
      }
    }
    sum += digit;
  }
  return sum % 10 === 0;
}

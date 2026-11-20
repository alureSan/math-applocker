# Roadmap

Ideas and planned work for future sessions.

## Done

- **Configurable operand digit count** — a 1–4 digit selector (plus "Auto", which
  follows the age preset) drives generated addition/subtraction ranges. Products
  are capped so answers stay readable, and division is always exact.
- **Custom problem sets** — named Room-backed sets with per-set switches,
  add/rename/delete, and per-set question management. Questions can be switched
  on or off individually. Old flat custom questions are migrated into an enabled
  "Custom" set.
- **Calculator validation** — numeric custom questions are parsed with standard
  precedence and parentheses. Invalid syntax, division by zero, non-whole
  division and out-of-range results are rejected; the answer can be calculated
  automatically or checked against the typed one.
- **Text-answer questions** — each question can use a free-text answer. Matching
  ignores case, accents and repeated whitespace. The challenge overlay switches
  from the numeric keypad to the system IME for these questions.
- **Multiplication tables** — 1×–9× toggles replace generated multiplication
  questions when at least one table is selected.
- **Basic and Advanced modes** — Basic uses generated questions from the age,
  digit and operation settings. Advanced uses only enabled challenge sets and
  multiplication tables, with a safe fallback to Basic when nothing is enabled.
  The main screen shows a compact green permission card when everything is
  granted, and a red card listing only the missing permissions otherwise.
- **Auto-saving settings** — no Save button to forget. Changing the age preset
  resets digits to Auto. The overlay and its notification use the in-app
  language even on Android 10, where services do not inherit AppCompat locales.

## Smaller follow-ups

- Reboot / `MY_PACKAGE_REPLACED` autostart test on EMUI and Samsung.
- Optional PIN brute-force backoff persistence test.
- Consider per-app unlock windows instead of one global unlock window.
- Add a short in-app "How it works" page explaining the three special permissions.
- Consider per-operation digit overrides if one global digit setting feels limiting.
- Consider accepting alternative spellings for text answers if parents request it.

## Next session: tablet and landscape QA

Test the debug and release builds on the Samsung tablet and on the phone in
landscape:

- PIN setup/auth layout. The keypad now uses a two-column layout with keys sized
  to the available height; verify on the tablet and on smaller phones.
- Home: status card, compact permission card, Basic/Advanced switch.
- Basic rules, advanced timing, multiplication table grid, set/question lists.
- Overlay: numeric keypad, text-question IME, progress, exit button, and the
  keyboard covering/reflowing the content.
- Overlay and notification language in Spanish.

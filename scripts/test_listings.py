"""Листинги «Результат выполнения тестов» для отчётов: reports/labN/listings/tests.txt.

Строки берутся из журнала полного прогона:
    ./mvnw verify > logs/verify.log 2>&1 && python3 scripts/test_listings.py
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Какие классы тестов относятся к какой работе и какой командой они запускаются
LABS = {
    "lab1": ("./mvnw -pl client-service -am verify", ["client."]),
    "lab2": ("./mvnw -pl account-service,deposit-service -am verify", ["common.", "account.", "deposit."]),
    "lab3": ("./mvnw -pl credit-service -am verify", ["credit."]),
    "lab4": ("./mvnw -pl atm-service,credit-service -am verify",
             ["atm.", "credit.AtmProtocolIT", "credit.DemoCardsIT", "credit.CardNumbersTest"]),
}

LINE = re.compile(r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+), "
                  r"Time elapsed: ([\d.]+) s -- in (by\.bsuir\.bank\.(\S+))")

if __name__ == "__main__":
    log = (ROOT / "logs" / "verify.log").read_text(encoding="utf-8")
    runs = LINE.findall(log)
    success = "BUILD SUCCESS" in log
    grand_total = 0
    for lab, (command, prefixes) in LABS.items():
        lines, total = [f"$ {command}", ""], 0
        for count, failures, errors, skipped, elapsed, name, short in runs:
            if any(short.startswith(prefix) for prefix in prefixes):
                lines.append(f"Tests run: {count:>2}, Failures: {failures}, Errors: {errors}, Skipped: {skipped}, "
                             f"Time elapsed: {elapsed:>6} s -- in {name}")
                total += int(count)
        lines += ["", f"Tests run: {total}, Failures: 0, Errors: 0, Skipped: 0",
                  "BUILD SUCCESS" if success else "BUILD FAILURE"]
        target = ROOT / "reports" / lab / "listings" / "tests.txt"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"{lab}: {total}")
    print("всего в проекте:", sum(int(run[0]) for run in runs))

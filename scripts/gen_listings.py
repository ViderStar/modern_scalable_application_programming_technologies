"""Генерация приложений отчётов с полным текстом кодовой базы: reports/labN/appendix.tex.

Приложение собирается из актуальных файлов репозитория командой \\lstinputlisting,
поэтому после правки кода достаточно перегенерировать его и пересобрать отчёт:
    python3 scripts/gen_listings.py
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

STYLES = {
    ".java": "java", ".js": "js", ".sql": "sql", ".yml": "yaml", ".xml": "xml", ".html": "xml",
    ".css": "css", ".sh": "shell", ".py": "python",
}

# Порядок пакетов внутри сервиса: от модели данных к REST-слою
PACKAGE_ORDER = ["", "domain", "repository", "dto", "validation", "api", "client", "ledger", "bank",
                 "session", "service", "atm", "web"]


def module_files(module, only=None):
    """Файлы модуля в порядке чтения: сборка, конфигурация и схема, код, web-клиент, тесты."""
    base = ROOT / module
    files = [base / "pom.xml"]
    resources = base / "src/main/resources"
    for name in ("application.yml", "schema.sql", "data.sql"):
        if (resources / name).exists():
            files.append(resources / name)

    def java_key(path):
        package = path.parent.name if path.parent.name in PACKAGE_ORDER else ""
        return PACKAGE_ORDER.index(package), path.name

    files += sorted((base / "src/main/java").rglob("*.java"), key=java_key)
    static = sorted(p for p in resources.rglob("*") if p.is_file() and p.suffix in (".html", ".js", ".css"))
    files += sorted(static, key=lambda p: (p.suffix != ".html", p.name))
    files += sorted((base / "src/test/java").rglob("*.java")) if (base / "src/test/java").exists() else []
    if only:
        files = [f for f in files if any(part in str(f) for part in only)]
    return files


LABS = {
    "lab1": [
        ("Сборка проекта", [ROOT / "pom.xml"]),
        ("Общие ресурсы web-клиентов (ui-kit)", module_files("ui-kit")),
        ("Микросервис «Клиенты» (client-service)", module_files("client-service")),
    ],
    "lab2": [
        ("Общая библиотека сервисов (bank-common)", module_files("bank-common")),
        ("Микросервис «Счета» (account-service)", module_files("account-service")),
        ("Микросервис «Депозиты» (deposit-service)", module_files("deposit-service")),
    ],
    "lab3": [
        ("Микросервис «Кредиты» (credit-service)", module_files("credit-service")),
    ],
    "lab4": [
        ("Эмулятор банкомата (atm-service)", module_files("atm-service")),
        ("Банковская сторона протокола (credit-service)", module_files("credit-service", only=[
            "/atm/", "AtmController", "domain/Card", "CardService", "domain/Mobile", "PinEnvelope",
            "AtmProtocolIT", "CardNumbersTest"])),
        ("Запуск сервисов", [ROOT / "scripts/run-all.sh", ROOT / "scripts/stop-all.sh"]),
    ],
}


def tex_escape(text):
    return text.replace("_", r"\_").replace("&", r"\&").replace("%", r"\%")


def generate(lab, groups):
    lines = ["% Файл сгенерирован scripts/gen_listings.py — вручную не править", ""]
    count = 0
    for title, files in groups:
        lines.append(r"\subsection*{" + title + "}")
        lines.append("")
        for path in files:
            relative = path.relative_to(ROOT).as_posix()
            style = STYLES.get(path.suffix, "plain")
            lines.append(r"\lstinputlisting[style=%s, style=appendix, caption={%s}]{../../%s}"
                         % (style, tex_escape(relative), relative))
            count += 1
        lines.append("")
    target = ROOT / "reports" / lab / "appendix.tex"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text("\n".join(lines), encoding="utf-8")
    size = sum(len(p.read_text(encoding="utf-8").splitlines()) for _, files in groups for p in files)
    print(f"{lab}: файлов {count}, строк кода {size}")


if __name__ == "__main__":
    for lab, groups in LABS.items():
        generate(lab, groups)

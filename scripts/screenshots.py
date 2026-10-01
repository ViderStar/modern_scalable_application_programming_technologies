"""Снимки экранов web-клиентов для отчётов: сценарий каждой лабораторной проходит в Chrome без окна.

Запуск (сервисы должны быть подняты, см. scripts/make-screenshots.sh):
    uv run --with selenium scripts/screenshots.py lab1
"""
import json
import sys
import time
import urllib.request
from pathlib import Path

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import Select, WebDriverWait

ROOT = Path(__file__).resolve().parent.parent
HOST = "http://localhost"
CLIENTS, ACCOUNTS, DEPOSITS, CREDITS, ATM = (f"{HOST}:{port}" for port in (8081, 8082, 8083, 8084, 8085))
WIDTH = 1240


class Browser:
    def __init__(self):
        options = webdriver.ChromeOptions()
        options.add_argument("--headless=new")
        options.add_argument("--force-device-scale-factor=1.5")
        options.add_argument("--hide-scrollbars")
        options.add_argument(f"--window-size={WIDTH},480")
        self.driver = webdriver.Chrome(options=options)
        self.wait = WebDriverWait(self.driver, 15)
        self.folder = ROOT

    def open(self, url):
        self.driver.get(url)
        time.sleep(0.6)

    def el(self, element_id):
        return self.wait.until(EC.presence_of_element_located((By.ID, element_id)))

    def click(self, element_id):
        self.wait.until(EC.element_to_be_clickable((By.ID, element_id))).click()
        time.sleep(0.35)

    def type(self, element_id, text):
        field = self.el(element_id)
        field.clear()
        field.send_keys(text)

    def choose(self, element_id, text):
        select = Select(self.el(element_id))
        for option in select.options:
            if text in option.text:
                select.select_by_visible_text(option.text)
                time.sleep(0.15)
                return
        raise ValueError(f"В списке {element_id} нет варианта «{text}»")

    def text(self, element_id):
        return self.el(element_id).text

    def wait_text(self, element_id, text):
        self.wait.until(EC.text_to_be_present_in_element((By.ID, element_id), text))
        time.sleep(0.3)

    def shot(self, name, height=None):
        """Снимок страницы целиком (или её верхних height пикселей) независимо от размера окна."""
        self.driver.execute_script("window.scrollTo(0, 0)")
        time.sleep(0.3)
        import base64
        size = self.driver.execute_cdp_cmd("Page.getLayoutMetrics", {})["cssContentSize"]
        # страница короче окна: обрезаем пустое поле под содержимым <main>
        bottom = self.driver.execute_script(
            "const m = document.querySelector('main'); return m ? m.getBoundingClientRect().bottom + 28 : null")
        full = min(size["height"], bottom) if bottom else size["height"]
        clip = {"x": 0, "y": 0, "width": WIDTH, "height": min(full, height or full), "scale": 1}
        image = self.driver.execute_cdp_cmd("Page.captureScreenshot", {"captureBeyondViewport": True, "clip": clip})
        target = self.folder / f"{name}.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(base64.b64decode(image["data"]))
        print("  ", target.relative_to(ROOT))

    def quit(self):
        self.driver.quit()


def set_lang(b, code):
    """Переключение языка интерфейса кнопкой в шапке: страница перезагружается."""
    b.click(f"lang-{code}")
    time.sleep(1.2)


def rest(method, url, body=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request) as response:
        text = response.read().decode()
        return json.loads(text) if text else None


def bank_date():
    return rest("GET", f"{ACCOUNTS}/api/bank-day")["date"]


def add_months(iso, months):
    year, month, day = map(int, iso.split("-"))
    month += months
    year, month = year + (month - 1) // 12, (month - 1) % 12 + 1
    return f"{year:04d}-{month:02d}-{day:02d}"


# ---------------------------------------------------------------- ЛР1

NEW_CLIENT = {
    "lastName": "Иванов", "firstName": "Иван", "middleName": "Иванович", "birthDate": "17.05.1990",
    "birthPlace": "г. Минск", "passportSeries": "MP", "passportNumber": "7654321",
    "identificationNumber": "3170590A077PB4", "issuedBy": "Фрунзенское РУВД г. Минска", "issueDate": "20.06.2018",
    "residenceAddress": "ул. Притыцкого, д. 29, кв. 3", "homePhone": "201-45-67",
    "mobilePhone": "+375 (29) 765-43-21", "email": "ivanov@example.by", "monthlyIncome": "2500.50",
}
NEW_CLIENT_SELECTS = {
    "residenceCityId": "Минск", "registrationCityId": "Борисов", "maritalStatusId": "Женат",
    "citizenshipId": "Республика Беларусь", "disabilityId": "Нет",
}
# позиции тех же значений в списках (города отсортированы по алфавиту на русском и белорусском одинаково)
NEW_CLIENT_SELECT_INDEX = {
    "residenceCityId": 7, "registrationCityId": 2, "maritalStatusId": 2, "citizenshipId": 1, "disabilityId": 1,
}


def fill_client(b, overrides=None):
    values = {**NEW_CLIENT, **(overrides or {})}
    for field, value in values.items():
        b.type(f"f-{field}", value)
    b.el("f-sex-M").click()
    for field in NEW_CLIENT_SELECTS:
        select = Select(b.el(f"f-{field}"))
        select.select_by_index(NEW_CLIENT_SELECT_INDEX[field])       # по номеру: подписи зависят от языка
        time.sleep(0.1)


def lab1(b):
    b.folder = ROOT / "reports" / "lab1" / "img"
    b.open(CLIENTS)
    b.shot("list")

    b.click("btn-add")
    fill_client(b)
    b.shot("form")

    # первичная (клиентская) валидация: цифры в фамилии, пробел вместо имени, 31 февраля, неполные маски
    fill_client(b, {"lastName": "1234", "firstName": " ", "birthDate": "31.02.2015", "passportNumber": "12345",
                    "mobilePhone": "+375 (17) 765-43-21", "email": "ivanov@"})
    b.click("btn-save")
    b.shot("form_errors")

    # сообщение сервера: паспорт и идентификационный номер уже заняты клиентом из начальных данных
    fill_client(b, {"passportNumber": "3141592", "identificationNumber": "3140301A001PB5"})
    b.click("btn-save")
    b.wait_text("form-alert", "уже есть")
    b.shot("form_duplicate")

    fill_client(b)
    b.click("btn-save")
    b.wait_text("notice", "Клиент добавлен")
    b.shot("list_added")

    # тот же интерфейс на английском и белорусском языках
    set_lang(b, "en")
    b.shot("list_en")
    set_lang(b, "be")
    b.click("btn-add")
    fill_client(b, {"lastName": "1234", "birthDate": "31.02.2015", "passportNumber": "3141592",
                    "identificationNumber": "3140301A001PB5"})
    b.click("btn-save")
    b.shot("form_be", height=760)
    set_lang(b, "ru")

    # проверка «прямо в базе» через консоль H2
    b.driver.set_window_size(WIDTH, 640)
    b.open(f"{CLIENTS}/h2-console")
    b.driver.find_element(By.NAME, "url").clear()
    b.driver.find_element(By.NAME, "url").send_keys("jdbc:h2:mem:clients")
    b.driver.find_element(By.CSS_SELECTOR, "input[type=submit][value=Connect]").click()
    time.sleep(1)
    b.driver.switch_to.frame("h2query")
    b.driver.execute_script(
        "document.getElementById('sql').value = arguments[0]",
        "SELECT ID, LAST_NAME, FIRST_NAME, PASSPORT_SERIES, PASSPORT_NUMBER, IDENTIFICATION_NUMBER FROM CLIENT ORDER BY LAST_NAME")
    b.driver.execute_script("submitAll()")
    time.sleep(1)
    b.driver.switch_to.default_content()
    b.shot("h2", height=640)
    b.driver.set_window_size(WIDTH, 480)


# ---------------------------------------------------------------- ЛР2

def open_deposit(b, client, product, amount, months, shot=None):
    b.click("btn-add")
    b.choose("f-clientId", client)
    b.choose("f-productId", product)
    b.type("f-amount", amount)
    b.type("f-termMonths", months)
    if shot:
        b.shot(shot)
    b.click("btn-save")
    b.wait_text("notice", "заключён")


def lab2(b):
    b.folder = ROOT / "reports" / "lab2" / "img"
    b.open(ACCOUNTS)
    b.shot("accounts_start")
    b.click("tab-chart")
    b.shot("chart")

    b.open(DEPOSITS)
    b.click("btn-add")
    b.choose("f-clientId", "Лебедевич")
    b.choose("f-productId", "Online-решение")
    b.type("f-amount", "100")
    b.type("f-termMonths", "24")
    b.type("f-number", "Д-12")
    b.click("btn-save")
    b.shot("form_errors")
    b.click("btn-cancel")

    open_deposit(b, "Лебедевич", "Свободное накопление", "10000", "3", shot="form")
    b.shot("details_open")
    b.click("btn-back")
    open_deposit(b, "Савицкая", "Online-решение", "5000", "3")
    b.click("btn-back")
    b.shot("list")

    b.open(ACCOUNTS)
    b.shot("report_open")

    # месяц работы банка: 31 закрытие дня
    b.type("days", "31")
    b.click("btn-close-days")
    b.wait_text("events", "выплачены проценты")
    b.shot("report_month")
    b.click("tab-journal")
    b.shot("journal", height=1100)
    # журнал хранит коды операций, поэтому те же проводки читаются на другом языке
    set_lang(b, "en")
    b.click("tab-journal")
    b.shot("journal_en", height=760)
    set_lang(b, "ru")

    b.open(DEPOSITS)
    b.driver.find_element(By.LINK_TEXT, "Д-000001").click()
    time.sleep(0.6)
    b.shot("details_month", height=1250)

    # до окончания обоих договоров: ещё два месяца
    b.type("days", "61")
    b.click("btn-close-days")
    b.wait_text("events", "срок договора истёк")
    b.click("btn-back")
    b.shot("list_closed")
    b.open(ACCOUNTS)
    b.shot("report_closed")


# ---------------------------------------------------------------- ЛР3

def open_credit(b, client, product, amount, months, shot=None):
    b.click("btn-add")
    b.choose("f-clientId", client)
    b.choose("f-productId", product)
    b.type("f-amount", amount)
    b.type("f-termMonths", months)
    if shot:
        b.click("btn-calc")
        b.wait_text("schedule", "Итого")
        b.shot(shot)
    b.click("btn-save")
    b.wait_text("notice", "заключён")
    return b.text("card-number").replace(" ", ""), b.text("card-pin")


def seed_deposits():
    """Два действующих вклада из предыдущей работы — для отчёта с десятью счетами."""
    today = bank_date()
    for number, product, client, amount, rate in (("Д-000001", 1, 1, 10000, 7.00), ("Д-000002", 2, 2, 5000, 12.90)):
        rest("POST", f"{DEPOSITS}/api/deposits", {
            "number": number, "productId": product, "currency": "BYN", "clientId": client, "amount": amount,
            "rate": rate, "termMonths": 12, "startDate": today, "endDate": add_months(today, 12)})


def lab3(b):
    b.folder = ROOT / "reports" / "lab3" / "img"
    seed_deposits()

    b.open(CREDITS)
    b.click("btn-add")
    b.choose("f-clientId", "Лебедевич")
    b.choose("f-productId", "R-Онлайн")
    b.type("f-amount", "20000")
    b.type("f-termMonths", "48")
    b.click("btn-save")
    b.shot("form_errors")
    b.click("btn-cancel")

    open_credit(b, "Лебедевич", "R-деньги mix", "12000", "6", shot="form_schedule")
    b.shot("details_open")
    b.type("cash-amount", "12000")
    b.click("btn-cash")
    b.wait_text("notice", "выдано")
    b.click("btn-back")
    open_credit(b, "Жуков", "R-Онлайн", "6000", "4")
    b.click("btn-back")
    b.shot("list")

    b.open(ACCOUNTS)
    b.shot("report_open")

    b.type("days", "31")
    b.click("btn-close-days")
    b.wait_text("events", "платёж №1")
    b.shot("report_month")

    b.open(CREDITS)
    b.driver.find_element(By.LINK_TEXT, "К-000001").click()
    time.sleep(0.6)
    b.shot("details_month", height=1750)
    b.click("btn-back")
    b.driver.find_element(By.LINK_TEXT, "К-000002").click()
    time.sleep(0.6)
    b.shot("details_interest_only")


# ---------------------------------------------------------------- ЛР4

def atm_keys(b, digits):
    for digit in digits:
        b.driver.find_element(By.ID, f"key-{digit}").click()
    time.sleep(0.2)


def atm_state(b, state):
    b.wait.until(EC.text_to_be_present_in_element_attribute((By.ID, "display"), "data-state", state))
    b.wait_text("state", state)
    time.sleep(0.5)


def atm_shot(b, name):
    b.shot(name)


def demo_cards():
    """Демо-карты банк выпускает в фоне после старта — ждём, пока появятся все три."""
    for _ in range(40):
        cards = rest("GET", f"{ATM}/api/demo-cards")
        if len(cards) == 3:
            return cards
        time.sleep(2)
    raise RuntimeError("Демо-карты не выпущены")


def atm_login(b, index, pin):
    b.click(f"demo-insert-{index}")
    b.click("key-enter")
    atm_state(b, "PIN")
    atm_keys(b, pin)
    b.click("key-enter")
    atm_state(b, "MENU")


def lab4(b):
    b.folder = ROOT / "reports" / "lab4" / "img"
    b.folder.mkdir(parents=True, exist_ok=True)
    cards = demo_cards()
    seed_deposits()                                    # вклад держателя первой карты — для запроса остатка депозита
    pin = cards[0]["pin"]
    wrong = "0000"

    b.open(ATM)
    atm_state(b, "INSERT_CARD")
    b.wait.until(EC.presence_of_element_located((By.ID, "demo-insert-2")))
    atm_shot(b, "demo_cards")
    b.click("demo-insert-0")                           # «вставить» демо-карту: номер подставлен в поле ввода
    atm_shot(b, "insert_card")
    b.click("key-enter")
    atm_state(b, "PIN")

    atm_keys(b, wrong)
    b.click("key-enter")
    b.wait_text("notice", "Неверный PIN-код")
    atm_shot(b, "wrong_pin")

    atm_keys(b, pin)
    atm_shot(b, "pin")
    b.click("key-enter")
    atm_state(b, "MENU")
    atm_shot(b, "menu")

    b.click("opt-WITHDRAW")
    atm_state(b, "AMOUNT")
    atm_keys(b, "450")
    atm_shot(b, "withdraw_amount")
    b.click("key-enter")
    atm_state(b, "RECEIPT_PROMPT")
    atm_shot(b, "withdraw_cash")
    b.click("opt-YES")
    atm_state(b, "MENU")
    atm_shot(b, "withdraw_receipt")

    # следующая операция: номер карты подставлен, PIN-код вводится заново
    b.click("opt-BALANCE")
    atm_state(b, "PIN")
    atm_shot(b, "pin_again")
    atm_keys(b, pin)
    b.click("key-enter")
    atm_state(b, "RECEIPT_PROMPT")
    b.click("opt-YES")
    atm_state(b, "RESULT")
    atm_shot(b, "balance")
    b.click("opt-CONTINUE")
    atm_state(b, "MENU")

    b.click("opt-PAYMENT")
    atm_state(b, "PIN")
    atm_keys(b, pin)
    b.click("key-enter")
    atm_state(b, "OPERATOR")
    atm_shot(b, "payment_operator")
    b.click("opt-MTS")
    atm_state(b, "PHONE")
    atm_keys(b, "0297654321")
    b.click("key-enter")
    atm_state(b, "PAY_AMOUNT")
    atm_keys(b, "25")
    b.click("key-enter")
    atm_state(b, "CONFIRM")
    atm_shot(b, "payment_confirm")
    b.click("opt-CONFIRM")
    atm_state(b, "MESSAGE")
    atm_shot(b, "payment_receipt")
    b.click("opt-CONTINUE")
    atm_state(b, "MENU")

    b.click("opt-WITHDRAW")
    atm_state(b, "PIN")
    atm_keys(b, pin)
    b.click("key-enter")
    atm_state(b, "AMOUNT")
    atm_keys(b, "9000")
    b.click("key-enter")
    atm_state(b, "MESSAGE")
    atm_shot(b, "insufficient")
    b.click("opt-CONTINUE")
    atm_state(b, "MENU")

    b.click("opt-DEPOSIT_BALANCE")
    atm_state(b, "PIN")
    atm_keys(b, pin)
    b.click("key-enter")
    atm_state(b, "RECEIPT_PROMPT")
    b.click("opt-YES")
    atm_state(b, "RESULT")
    atm_shot(b, "deposit_balance")
    b.click("opt-EJECT")
    atm_state(b, "INSERT_CARD")
    atm_shot(b, "ejected")

    # три неверных PIN-кода подряд — третья демо-карта блокируется банком
    b.click("demo-insert-2")
    b.click("key-enter")
    atm_state(b, "PIN")
    for _ in range(3):
        atm_keys(b, wrong)
        b.click("key-enter")
        time.sleep(1.2)
    atm_state(b, "INSERT_CARD")
    time.sleep(1.0)
    atm_shot(b, "blocked")

    # тот же банкомат на английском и белорусском: экран и чек формирует сервис на языке клиента
    set_lang(b, "en")
    atm_state(b, "INSERT_CARD")
    b.wait.until(EC.presence_of_element_located((By.ID, "demo-insert-1")))
    atm_login(b, 1, cards[1]["pin"])
    atm_shot(b, "lang_en")
    set_lang(b, "be")
    atm_state(b, "INSERT_CARD")
    b.wait.until(EC.presence_of_element_located((By.ID, "demo-insert-1")))
    atm_login(b, 1, cards[1]["pin"])
    b.click("opt-WITHDRAW")
    atm_state(b, "AMOUNT")
    atm_keys(b, "100")
    b.click("key-enter")
    atm_state(b, "RECEIPT_PROMPT")
    b.click("opt-YES")
    atm_state(b, "MENU")
    atm_shot(b, "lang_be")
    set_lang(b, "ru")

    b.open(CREDITS)
    b.driver.find_element(By.LINK_TEXT, cards[0]["contract"]).click()
    time.sleep(0.6)
    b.shot("bank_side", height=1500)
    b.open(ACCOUNTS)
    b.shot("report")


LABS = {"lab1": lab1, "lab2": lab2, "lab3": lab3, "lab4": lab4}

if __name__ == "__main__":
    browser = Browser()
    try:
        for lab in sys.argv[1:]:
            print(lab)
            LABS[lab](browser)
    finally:
        browser.quit()

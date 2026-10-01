/* Клиентский интерфейс банкомата: «тонкий» терминал. Вся логика сеанса — на сервере (AtmSession),
   сюда приходит готовое описание экрана, отсюда уходят события: ввод, выбор пункта меню, отмена. */

const t = Bank.t;

const app = Bank.createApp({
  data() {
    return {
      sessionId: null,
      screen: null,
      typed: '',
      busy: false,
      busyText: '',
      error: '',
      demoCards: [],
      demoAttempts: 0,
    };
  },
  computed: {
    /* Пункты меню прижимаются к нижним боковым кнопкам: первые четыре — справа, остальные — слева. */
    rightOptions() {
      return this.column(this.screen.options.slice(0, 4));
    },
    leftOptions() {
      return this.column(this.screen.options.slice(4, 8));
    },
    shownInput() {
      const input = this.screen.input;
      if (!input) return '';
      if (input.kind === 'PIN') return '●'.repeat(this.typed.length);
      if (input.kind === 'CARD') return this.cardNumber(this.typed);
      return this.typed;
    },
    reply() {
      const exchange = this.screen.exchange;
      return exchange && exchange.reply ? JSON.stringify(exchange.reply, null, 2) : t('atm.noReply');
    },
  },
  async mounted() {
    window.addEventListener('keydown', this.keyboard);
    await this.open();
    this.loadDemoCards();
  },
  methods: {
    column(options) {
      const rows = [null, null, null, null];
      options.forEach((option, i) => { rows[4 - options.length + i] = option; });
      return rows;
    },
    cardNumber(number) {
      return number.replace(/(\d{4})(?=\d)/g, '$1 ');
    },
    async open() {
      try {
        const session = await Bank.api('POST', '/api/sessions');
        this.sessionId = session.id;
        this.screen = session.screen;
      } catch (e) {
        this.error = e.message;
      }
    },
    /* Демо-карты банк выпускает в фоне после старта, поэтому пустой список перечитывается несколько раз. */
    async loadDemoCards() {
      try {
        this.demoCards = await Bank.api('GET', '/api/demo-cards');
      } catch (e) {
        this.demoCards = [];
      }
      if (!this.demoCards.length && this.demoAttempts++ < 10) setTimeout(this.loadDemoCards, 3000);
    },
    /* «Вставить» демо-карту: её номер подставляется в поле ввода, остаётся нажать «Ввод». */
    insertDemo(card) {
      if (this.screen.state === 'INSERT_CARD' && !this.busy) this.typed = card.cardNumber;
    },
    /* Отправка события автомату банкомата. Пока банк отвечает, на экране — «Авторизация на сервере...». */
    async send(action, body, waitText) {
      if (this.busy) return;
      this.busy = true;
      this.busyText = waitText;
      this.error = '';
      try {
        const pause = new Promise(resolve => setTimeout(resolve, waitText ? 500 : 0));
        const next = await Bank.api('POST', '/api/sessions/' + this.sessionId + '/' + action, body);
        await pause;
        this.screen = next;
        this.typed = '';
        if (next.state === 'INSERT_CARD') this.loadDemoCards();      // обновить признак блокировки
      } catch (e) {
        if (e.status === 404) await this.open();       // сервис перезапущен — начинаем новый сеанс
        else this.error = e.message;
      } finally {
        this.busy = false;
      }
    },
    digit(d) {
      const input = this.screen.input;
      if (input && !this.busy && this.typed.length < input.length) this.typed += d;
    },
    clear() {
      this.typed = '';
    },
    enter() {
      if (!this.screen.input) return;
      const state = this.screen.state;
      const wait = state === 'PIN' ? t('atm.authorizing') : (state === 'AMOUNT' ? t('atm.requesting') : '');
      this.send('enter', { value: this.typed }, wait);
    },
    select(option) {
      if (!option) return;
      // запрос остатка уходит в банк сразу, только если в списке ещё лежат данные карты
      const hasPin = this.screen.buffer.some(item => item.name === 'PIN');
      const toBank = option.key === 'CONFIRM' || (hasPin && ['BALANCE', 'DEPOSIT_BALANCE'].includes(option.key));
      this.send('select', { option: option.key }, toBank ? t('atm.requesting') : '');
    },
    cancel() {
      this.send('cancel', undefined, '');
    },
    keyboard(event) {
      if (!this.screen) return;
      if (/^\d$/.test(event.key)) this.digit(event.key);
      else if (event.key === 'Enter') this.enter();
      else if (event.key === 'Escape') this.cancel();
      else if (event.key === 'Backspace') this.typed = this.typed.slice(0, -1);
    },
  },
  template: `
    <bank-nav active="atm"></bank-nav>
    <main v-if="screen" class="atm-page">
      <div>
        <div v-if="error" id="error" class="alert error">{{ error }}</div>
        <div class="atm">
          <div class="atm-head">
            <div class="atm-logo">BankEt<small>{{ t('atm.logo') }}</small></div>
            <div class="card-slot">
              <div class="slot-label">{{ t('atm.card') }}</div>
              <div class="slot" :class="{ inserted: screen.cardInside }" id="card-slot"></div>
            </div>
          </div>

          <div class="atm-body">
            <div class="side">
              <button v-for="(option, i) in leftOptions" :key="'l' + i" :id="option ? 'opt-' + option.key : null"
                      :disabled="!option || busy" @click="select(option)"></button>
            </div>

            <div class="display" id="display" :data-state="screen.state">
              <div class="display-top">
                <div class="display-title" id="display-title">{{ busy && busyText ? busyText : screen.title }}</div>
                <div v-if="!(busy && busyText)" class="display-lines" id="display-lines">
                  <div v-for="(line, i) in screen.lines" :key="i">{{ line }}</div>
                </div>
              </div>
              <div class="display-main">
                <div class="display-center" v-if="!(busy && busyText)">
                  <div v-if="screen.input" class="display-input" id="display-input">{{ shownInput }}</div>
                  <div v-if="screen.notice" class="display-notice" id="notice" :class="{ ok: screen.noticeOk }">{{ screen.notice }}</div>
                </div>
                <div class="display-row" v-for="row in 4" :key="row">
                  <span class="opt left" v-if="leftOptions[row - 1] && !busy">{{ leftOptions[row - 1].label }}</span>
                  <span class="opt right" v-if="rightOptions[row - 1] && !busy">{{ rightOptions[row - 1].label }}</span>
                </div>
              </div>
            </div>

            <div class="side">
              <button v-for="(option, i) in rightOptions" :key="'r' + i" :id="option ? 'opt-' + option.key : null"
                      :disabled="!option || busy" @click="select(option)"></button>
            </div>
          </div>

          <div class="atm-foot">
            <div class="keypad">
              <button v-for="d in ['1', '2', '3']" :key="d" :id="'key-' + d" @click="digit(d)">{{ d }}</button>
              <button id="key-cancel" class="fn cancel" @click="cancel">{{ t('atm.cancel') }}</button>
              <button v-for="d in ['4', '5', '6']" :key="d" :id="'key-' + d" @click="digit(d)">{{ d }}</button>
              <button id="key-clear" class="fn clear" @click="clear">{{ t('atm.clear') }}</button>
              <button v-for="d in ['7', '8', '9']" :key="d" :id="'key-' + d" @click="digit(d)">{{ d }}</button>
              <button id="key-enter" class="fn enter" @click="enter">{{ t('atm.enter') }}</button>
              <span></span>
              <button id="key-0" @click="digit('0')">0</button>
            </div>
            <div class="outputs">
              <div>
                <div class="slot-label">{{ t('atm.cashSlot') }}</div>
                <div class="slot"></div>
                <div v-if="screen.cash" class="cash-notes" id="cash">{{ screen.cash }}</div>
              </div>
              <div>
                <div class="slot-label">{{ t('atm.receiptSlot') }}</div>
                <div class="slot"></div>
                <div v-if="screen.receipt" class="receipt" id="receipt">{{ screen.receipt.join('\\n') }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <aside class="inspector">
        <div class="card" id="demo-cards">
          <h2>{{ t('atm.demoTitle') }}</h2>
          <p class="muted" style="margin: 0 0 10px">{{ demoCards.length ? t('atm.demoHint') : t('atm.demoNone') }}</p>
          <div v-for="(card, i) in demoCards" :key="card.cardNumber" class="demo-card" :class="{ blocked: card.blocked }">
            <div>
              <div class="demo-number" :id="'demo-number-' + i">{{ cardNumber(card.cardNumber) }}</div>
              <div class="demo-holder">{{ card.holder }}</div>
            </div>
            <div class="demo-pin">
              <span class="muted">{{ t('atm.demoPin') }}</span>
              <b :id="'demo-pin-' + i">{{ card.pin || t('atm.demoPinChanged') }}</b>
              <span v-if="card.blocked" class="badge bad">{{ t('atm.demoBlocked') }}</span>
            </div>
            <button class="small" :id="'demo-insert-' + i" :disabled="screen.state !== 'INSERT_CARD' || busy"
                    @click="insertDemo(card)">{{ t('atm.demoInsert') }}</button>
          </div>
        </div>
        <div class="card">
          <h2>{{ t('atm.stateTitle') }}</h2>
          <div class="state" id="state">{{ screen.state }}</div>
          <p class="muted" style="margin: 8px 0 0">{{ t('atm.keyboardHint') }}</p>
        </div>
        <div class="card">
          <h2>{{ t('atm.bufferTitle') }}</h2>
          <div class="mono-list" id="buffer">
            <div v-for="(item, i) in screen.buffer" :key="i"><span class="name">{{ item.name }}</span>{{ item.value }}</div>
            <div v-if="!screen.buffer.length" class="muted">{{ t('atm.bufferEmpty') }}</div>
          </div>
        </div>
        <div class="card" v-if="screen.exchange">
          <h2>{{ t('atm.exchangeTitle') }}</h2>
          <div class="muted">{{ t('atm.request') }}</div>
          <div class="mono-list" id="request">
            <div v-for="(item, i) in screen.exchange.request" :key="i"><span class="name">{{ item.name }}</span>{{ item.value }}</div>
          </div>
          <div class="muted" style="margin-top: 10px">{{ t('atm.reply') }}</div>
          <pre id="reply">{{ reply }}</pre>
        </div>
      </aside>
    </main>`,
}, 'atm.page');

app.mount('#app');

/* Общие помощники web-клиентов банка: REST-вызовы, маски ввода, форматирование. */
const Bank = {

  /* Адреса web-клиентов остальных микросервисов для общей навигации. */
  services: [
    { key: 'clients',  title: 'Клиенты',  port: 8081 },
    { key: 'accounts', title: 'Счета',    port: 8082 },
    { key: 'deposits', title: 'Депозиты', port: 8083 },
    { key: 'credits',  title: 'Кредиты',  port: 8084 },
    { key: 'atm',      title: 'Банкомат', port: 8085 },
  ],

  /* REST-вызов: при ошибке бросает { status, message, fields } из тела ответа сервиса. */
  async api(method, url, body) {
    const options = { method, headers: { 'Accept': 'application/json' } };
    if (body !== undefined) {
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }
    let response;
    try {
      response = await fetch(url, options);
    } catch (e) {
      throw { status: 0, message: 'Сервис недоступен', fields: {} };
    }
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
      throw {
        status: response.status,
        message: (data && data.message) || 'Ошибка ' + response.status,
        fields: (data && data.fields) || {},
      };
    }
    return data;
  },

  /* Маска ввода: # — цифра, A — латинская буква (в верхнем регистре), остальное — литералы. */
  mask(value, pattern) {
    const raw = String(value ?? '');
    let out = '';
    let i = 0;
    for (const p of pattern) {
      if (i >= raw.length) break;
      if (p === '#' || p === 'A') {
        const allowed = p === '#' ? /\d/ : /[a-zA-Z]/;
        while (i < raw.length && !allowed.test(raw[i])) i++;
        if (i >= raw.length) break;
        out += raw[i++].toUpperCase();
      } else {
        out += p;
        if (raw[i] === p) i++;
      }
    }
    return out;
  },

  /* Директива v-mask="'##.##.####'" — применяет маску при каждом вводе. */
  maskDirective: {
    mounted(el, binding) {
      el.addEventListener('input', () => {
        const masked = Bank.mask(el.value, binding.value);
        if (masked !== el.value) {
          el.value = masked;
          el.dispatchEvent(new Event('input'));
        }
      });
    },
  },

  /* Строгий разбор даты ДД.ММ.ГГГГ: 31.02.2015 и 29.02.2015 датами не считаются. */
  parseDate(text) {
    const m = /^(\d{2})\.(\d{2})\.(\d{4})$/.exec(text || '');
    if (!m) return null;
    const [day, month, year] = [Number(m[1]), Number(m[2]), Number(m[3])];
    const date = new Date(year, month - 1, day);
    const same = date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
    return same ? date : null;
  },

  money(value) {
    if (value === null || value === undefined || value === '') return '';
    return Number(value).toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  },

  /* ISO-дата 2026-10-01 -> 01.10.2026 */
  date(iso) {
    return iso ? String(iso).split('-').reverse().join('.') : '';
  },

  /* Панель банковского дня: текущая дата и процедура «Закрытие банковского дня». */
  dayComponent: {
    emits: ['changed', 'failed'],
    data() {
      return { date: null, days: 30, busy: false };
    },
    async mounted() {
      try {
        this.date = (await Bank.api('GET', '/api/bank-day')).date;
      } catch (e) {
        this.$emit('failed', e);
      }
    },
    methods: {
      async close(days) {
        this.busy = true;
        try {
          const result = await Bank.api('POST', '/api/bank-day/close?days=' + days);
          this.date = result.bankDate;
          this.$emit('changed', result);
        } catch (e) {
          this.$emit('failed', e);
        } finally {
          this.busy = false;
        }
      },
      format: iso => Bank.date(iso),
    },
    template: `
      <div class="bankday">
        <span class="label">Банковский день</span>
        <b id="bank-date">{{ format(date) }}</b>
        <button id="btn-close-day" class="primary" :disabled="busy" @click="close(1)">Закрыть день</button>
        <input id="days" type="text" v-model.number="days" maxlength="4">
        <button id="btn-close-days" :disabled="busy || !(days > 0)" @click="close(days)">Закрыть дней</button>
      </div>`,
  },

  /* Шапка с переходами между web-клиентами микросервисов. */
  navComponent: {
    props: ['active'],
    data() {
      return { services: Bank.services, host: location.protocol + '//' + location.hostname };
    },
    template: `
      <header class="topbar">
        <div class="brand">Банк «Решение» <span>учебная АБС</span></div>
        <nav>
          <a v-for="s in services" :key="s.key" :href="host + ':' + s.port + '/'"
             :class="{ active: s.key === active }">{{ s.title }}</a>
        </nav>
      </header>`,
  },
};

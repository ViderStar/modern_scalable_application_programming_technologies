/* Web-клиент главной книги: отчёт о состоянии счетов, журнал проводок, план счетов, закрытие дня. */

const app = Vue.createApp({
  data() {
    return {
      tab: 'report',           // report | journal | chart
      report: { bankDate: null, accounts: [], totals: [] },
      journal: [],
      chart: [],
      currency: 'BYN',
      events: [],
      error: '',
    };
  },
  computed: {
    currencies() {
      return [...new Set(this.report.accounts.map(a => a.currency))];
    },
    accounts() {
      return this.report.accounts.filter(a => a.currency === this.currency);
    },
    total() {
      return this.report.totals.find(t => t.currency === this.currency) || { debit: 0, credit: 0 };
    },
    fund() {
      return this.accounts.find(a => a.chartCode === '7327' && !a.contractRef);
    },
    cash() {
      return this.accounts.find(a => a.chartCode === '1010');
    },
  },
  async mounted() {
    try {
      this.chart = await Bank.api('GET', '/api/chart-of-accounts');
      await this.reload();
    } catch (e) {
      this.error = e.message;
    }
  },
  methods: {
    async reload() {
      this.report = await Bank.api('GET', '/api/report');
      this.journal = await Bank.api('GET', '/api/operations?limit=300');
    },
    async closed(result) {
      this.error = '';
      this.events = result.events;
      await this.reload();
    },
    date: Bank.date,
    money: Bank.money,
  },
  template: `
    <bank-nav active="accounts"></bank-nav>
    <main>
      <div class="toolbar">
        <div>
          <h1>Счета банка</h1>
          <p class="subtitle">Отчёт о состоянии счетов и процедура «Закрытие банковского дня»</p>
        </div>
        <bank-day @changed="closed" @failed="e => error = e.message"></bank-day>
      </div>

      <div v-if="error" id="error" class="alert error">{{ error }}</div>

      <div class="stats">
        <div class="stat"><div class="label">Отчёт на дату</div><div class="value">{{ date(report.bankDate) }}</div></div>
        <div class="stat"><div class="label">Счетов в отчёте</div><div class="value" id="accounts-count">{{ accounts.length }}</div></div>
        <div class="stat"><div class="label">Фонд развития, {{ currency }}</div><div class="value">{{ fund ? money(fund.balance) : '—' }}</div></div>
        <div class="stat"><div class="label">Касса, {{ currency }}</div><div class="value">{{ cash ? money(cash.balance) : '—' }}</div></div>
      </div>

      <div v-if="events.length" id="events" class="alert info">
        <b>Протокол закрытия дня</b>
        <div v-for="(event, i) in events.slice(-12)" :key="i">{{ event }}</div>
        <div v-if="events.length > 12" class="muted">… всего записей: {{ events.length }}</div>
      </div>

      <div class="toolbar">
        <div style="display: flex; gap: 6px">
          <button :class="{ primary: tab === 'report' }" @click="tab = 'report'">Отчёт по счетам</button>
          <button :class="{ primary: tab === 'journal' }" @click="tab = 'journal'">Журнал проводок</button>
          <button :class="{ primary: tab === 'chart' }" @click="tab = 'chart'">План счетов</button>
        </div>
        <div v-if="tab === 'report'" style="display: flex; gap: 6px">
          <button v-for="c in currencies" :key="c" class="small" :class="{ primary: c === currency }" @click="currency = c">{{ c }}</button>
        </div>
      </div>

      <div v-if="tab === 'report'" class="card">
        <table id="report">
          <thead>
            <tr>
              <th>Номер счёта</th><th>Код</th><th>Название счёта</th><th>Активность</th><th>Договор</th>
              <th class="num">Дебет</th><th class="num">Кредит</th><th class="num">Сальдо</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in accounts" :key="a.number">
              <td class="mono">{{ a.number }}</td>
              <td class="mono">{{ a.chartCode }}</td>
              <td>{{ a.name }}<div class="muted" style="font-size: 12.5px">{{ a.chartName }}</div></td>
              <td><span class="badge" :class="{ ok: a.activity === 'ACTIVE' }">{{ a.activityTitle }}</span></td>
              <td class="mono">{{ a.contractRef }}</td>
              <td class="num">{{ money(a.debit) }}</td>
              <td class="num">{{ money(a.credit) }}</td>
              <td class="num"><b>{{ money(a.balance) }}</b></td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="5">Итого обороты, {{ currency }}</td>
              <td class="num">{{ money(total.debit) }}</td>
              <td class="num">{{ money(total.credit) }}</td>
              <td></td>
            </tr>
          </tfoot>
        </table>
      </div>

      <div v-if="tab === 'journal'" class="card">
        <table id="journal">
          <thead>
            <tr><th>№</th><th>Дата</th><th>Операция</th><th>Договор</th><th>Счёт</th><th class="num">Дебет</th><th class="num">Кредит</th></tr>
          </thead>
          <tbody>
            <template v-for="op in journal" :key="op.id">
              <tr v-for="(entry, i) in op.entries" :key="op.id + '-' + i">
                <td>{{ i === 0 ? op.id : '' }}</td>
                <td>{{ i === 0 ? date(op.bankDate) : '' }}</td>
                <td>{{ i === 0 ? op.description : '' }}</td>
                <td class="mono">{{ i === 0 ? op.contractRef : '' }}</td>
                <td class="mono">{{ entry.account }} <span class="muted">{{ entry.accountName }}</span></td>
                <td class="num">{{ entry.side === 'DEBIT' ? money(entry.amount) : '' }}</td>
                <td class="num">{{ entry.side === 'CREDIT' ? money(entry.amount) : '' }}</td>
              </tr>
            </template>
          </tbody>
        </table>
        <div v-if="!journal.length" class="empty">Проводок пока нет</div>
      </div>

      <div v-if="tab === 'chart'" class="card">
        <table id="chart">
          <thead><tr><th>Балансовый счёт</th><th>Наименование</th><th>Активность</th></tr></thead>
          <tbody>
            <tr v-for="c in chart" :key="c.code">
              <td class="mono">{{ c.code }}</td>
              <td>{{ c.name }}</td>
              <td>{{ { ACTIVE: 'Активный', PASSIVE: 'Пассивный', ACTIVE_PASSIVE: 'Активно-пассивный' }[c.activity] }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </main>`,
});

app.component('bank-nav', Bank.navComponent);
app.component('bank-day', Bank.dayComponent);
app.directive('mask', Bank.maskDirective);
app.mount('#app');

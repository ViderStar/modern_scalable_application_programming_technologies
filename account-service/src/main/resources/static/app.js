/* Web-клиент главной книги: отчёт о состоянии счетов, журнал проводок, план счетов, закрытие дня. */

const app = Bank.createApp({
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
          <h1>{{ t('accounts.title') }}</h1>
          <p class="subtitle">{{ t('accounts.subtitle') }}</p>
        </div>
        <bank-day @changed="closed" @failed="e => error = e.message"></bank-day>
      </div>

      <div v-if="error" id="error" class="alert error">{{ error }}</div>

      <div class="stats">
        <div class="stat"><div class="label">{{ t('accounts.reportDate') }}</div><div class="value">{{ date(report.bankDate) }}</div></div>
        <div class="stat"><div class="label">{{ t('accounts.count') }}</div><div class="value" id="accounts-count">{{ accounts.length }}</div></div>
        <div class="stat"><div class="label">{{ t('accounts.fund', currency) }}</div><div class="value">{{ fund ? money(fund.balance) : '—' }}</div></div>
        <div class="stat"><div class="label">{{ t('accounts.cash', currency) }}</div><div class="value">{{ cash ? money(cash.balance) : '—' }}</div></div>
      </div>

      <div v-if="events.length" id="events" class="alert info">
        <b>{{ t('events.title') }}</b>
        <div v-for="(event, i) in events.slice(-12)" :key="i">{{ event }}</div>
        <div v-if="events.length > 12" class="muted">{{ t('events.more', events.length) }}</div>
      </div>

      <div class="toolbar">
        <div style="display: flex; gap: 6px">
          <button id="tab-report" :class="{ primary: tab === 'report' }" @click="tab = 'report'">{{ t('accounts.tabReport') }}</button>
          <button id="tab-journal" :class="{ primary: tab === 'journal' }" @click="tab = 'journal'">{{ t('accounts.tabJournal') }}</button>
          <button id="tab-chart" :class="{ primary: tab === 'chart' }" @click="tab = 'chart'">{{ t('accounts.tabChart') }}</button>
        </div>
        <div v-if="tab === 'report'" style="display: flex; gap: 6px">
          <button v-for="c in currencies" :key="c" class="small" :class="{ primary: c === currency }" @click="currency = c">{{ c }}</button>
        </div>
      </div>

      <div v-if="tab === 'report'" class="card">
        <table id="report">
          <thead>
            <tr>
              <th>{{ t('table.account') }}</th><th>{{ t('table.code') }}</th><th>{{ t('accounts.name') }}</th>
              <th>{{ t('table.activity') }}</th><th>{{ t('table.contract') }}</th>
              <th class="num">{{ t('table.debit') }}</th><th class="num">{{ t('table.credit') }}</th><th class="num">{{ t('table.balance') }}</th>
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
              <td colspan="5">{{ t('accounts.totals', currency) }}</td>
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
            <tr><th>№</th><th>{{ t('table.date') }}</th><th>{{ t('table.operation') }}</th><th>{{ t('table.contract') }}</th>
                <th>{{ t('table.accountShort') }}</th><th class="num">{{ t('table.debit') }}</th><th class="num">{{ t('table.credit') }}</th></tr>
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
        <div v-if="!journal.length" class="empty">{{ t('accounts.noPostings') }}</div>
      </div>

      <div v-if="tab === 'chart'" class="card">
        <table id="chart">
          <thead><tr><th>{{ t('accounts.chartCode') }}</th><th>{{ t('accounts.chartName') }}</th><th>{{ t('table.activity') }}</th></tr></thead>
          <tbody>
            <tr v-for="c in chart" :key="c.code">
              <td class="mono">{{ c.code }}</td>
              <td>{{ c.name }}</td>
              <td>{{ c.activityTitle }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </main>`,
}, 'accounts.page');

app.mount('#app');

insert into currency (code, name, name_en, name_be) values
    ('BYN', 'Белорусский рубль', 'Belarusian ruble', 'Беларускі рубель'),
    ('USD', 'Доллар США', 'US dollar', 'Долар ЗША'),
    ('EUR', 'Евро', 'Euro', 'Еўра');

-- Балансовые счета, необходимые для депозитных и кредитных программ физических лиц
insert into chart_of_accounts (code, activity, name, name_en, name_be) values
    ('1010', 'ACTIVE', 'Денежные средства в кассе',
     'Cash on hand', 'Грашовыя сродкі ў касе'),
    ('1201', 'ACTIVE', 'Корреспондентский счёт в Национальном банке',
     'Correspondent account with the National Bank', 'Карэспандэнцкі рахунак у Нацыянальным банку'),
    ('2400', 'ACTIVE', 'Кредиты физическим лицам',
     'Loans to individuals', 'Крэдыты фізічным асобам'),
    ('2470', 'ACTIVE', 'Начисленные процентные доходы по кредитам физическим лицам',
     'Accrued interest income on loans to individuals', 'Налічаныя працэнтныя даходы па крэдытах фізічным асобам'),
    ('3012', 'PASSIVE', 'Текущие (расчётные) счета коммерческих организаций',
     'Current (settlement) accounts of commercial organisations', 'Бягучыя (разліковыя) рахункі камерцыйных арганізацый'),
    ('3014', 'PASSIVE', 'Текущие (расчётные) счета физических лиц',
     'Current (settlement) accounts of individuals', 'Бягучыя (разліковыя) рахункі фізічных асоб'),
    ('3404', 'PASSIVE', 'Вклады (депозиты) до востребования физических лиц',
     'Demand deposits of individuals', 'Уклады (дэпазіты) да запатрабавання фізічных асоб'),
    ('3414', 'PASSIVE', 'Срочные вклады (депозиты) физических лиц',
     'Term deposits of individuals', 'Тэрміновыя ўклады (дэпазіты) фізічных асоб'),
    ('3470', 'PASSIVE', 'Начисленные процентные расходы по вкладам (депозитам) до востребования',
     'Accrued interest expenses on demand deposits', 'Налічаныя працэнтныя выдаткі па ўкладах (дэпазітах) да запатрабавання'),
    ('3471', 'PASSIVE', 'Начисленные процентные расходы по срочным вкладам (депозитам)',
     'Accrued interest expenses on term deposits', 'Налічаныя працэнтныя выдаткі па тэрміновых укладах (дэпазітах)'),
    ('7327', 'PASSIVE', 'Фонд развития банка',
     'Bank development fund', 'Фонд развіцця банка');

insert into bank_day (id, bank_date) values (1, current_date);

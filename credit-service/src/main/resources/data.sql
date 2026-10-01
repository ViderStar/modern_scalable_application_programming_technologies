-- Кредитные программы банка «Решение» (вариант 4): условия приближены к действующим на rbank.by
insert into credit_product (name, kind, currency, rate, min_amount, max_amount, min_term_months, max_term_months, description)
values ('R-деньги mix', 'ANNUITY', 'BYN', 17.65, 500.00, 50000.00, 6, 60,
        'Кредит на потребительские нужды, погашение равными (аннуитетными) платежами'),
       ('R-Онлайн', 'INTEREST_ONLY', 'BYN', 17.65, 300.00, 15000.00, 3, 36,
        'Кредит с ежемесячной уплатой процентов и возвратом всей суммы в конце срока');

insert into mobile_operator (code, name) values
    ('A1', 'A1'),
    ('MTS', 'МТС'),
    ('LIFE', 'life:)');

-- Кредитные программы по образцу банка «Решение» (вариант 4): условия приближены к действующим на rbank.by
insert into credit_product (name, name_en, name_be, kind, currency, rate, min_amount, max_amount,
                            min_term_months, max_term_months, description, description_en, description_be)
values ('R-деньги mix', 'R-Money mix', 'R-грошы mix', 'ANNUITY', 'BYN', 17.65, 500.00, 50000.00, 6, 60,
        'Кредит на потребительские нужды, погашение равными (аннуитетными) платежами',
        'Consumer loan repaid in equal (annuity) instalments',
        'Крэдыт на спажывецкія патрэбы, пагашэнне роўнымі (ануітэтнымі) плацяжамі'),
       ('R-Онлайн', 'R-Online', 'R-Анлайн', 'INTEREST_ONLY', 'BYN', 17.65, 300.00, 15000.00, 3, 36,
        'Кредит с ежемесячной уплатой процентов и возвратом всей суммы в конце срока',
        'Loan with monthly interest payments and repayment of the principal at the end of the term',
        'Крэдыт са штомесячнай выплатай працэнтаў і вяртаннем усёй сумы ў канцы тэрміну');

insert into mobile_operator (code, name) values
    ('A1', 'A1'),
    ('MTS', 'МТС'),
    ('LIFE', 'life:)');

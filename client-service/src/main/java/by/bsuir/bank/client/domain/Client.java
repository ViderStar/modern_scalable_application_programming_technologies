package by.bsuir.bank.client.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Клиент банка — физическое лицо. */
@Entity
@Getter
@Setter
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String lastName;
    private String firstName;
    private String middleName;
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    private String passportSeries;
    private String passportNumber;
    private String issuedBy;
    private LocalDate issueDate;
    private String identificationNumber;
    private String birthPlace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private City residenceCity;

    private String residenceAddress;
    private String homePhone;
    private String mobilePhone;
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private City registrationCity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private MaritalStatus maritalStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Citizenship citizenship;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Disability disability;

    private boolean pensioner;
    private BigDecimal monthlyIncome;

    public String getFullName() {
        return lastName + " " + firstName + " " + middleName;
    }
}

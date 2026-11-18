# S4 · Плагин платформасы (Plugin Platform)

Автор: Амангелді Мадияр · ВТиПО, 4 курс

## Қысқаша: бұл не және қалай іске қосады

Платформа хостты өзгертпей JAR-плагиндерді жүктейді және басқарады.

```bash
mvn verify                                       # build + тесттер + coverage
mvn org.pitest:pitest-maven:mutationCoverage     # mutation testing
```

## Архитектура

_(толтырылады)_

## Қабылдау шарттарының статусы

| Шарт | Нәтиже | Дәлел (тест аты / өлшем файлы) |
|------|--------|-------------------------------|
| Зиянды плагин хостты тоқтата алмайды | ⏳ | |
| Line coverage ≥ 80% | ⏳ | target/site/jacoco |
| Mutation score ≥ 60% | ⏳ | target/pit-reports |

## Үлгілер

[docs/PATTERN_REGISTER.md](docs/PATTERN_REGISTER.md)

## Белгілі шектеулер және ашық сұрақтар

_(толтырылады)_

## AI қолдану

_(толтырылады)_

# PATTERN_REGISTER · S4 Плагин платформасы

## Қолданылған үлгілер

| Үлгі | Класс(тар) | Қандай мәселені шешеді | Талап | Дәлел (тест) |
|------|-----------|------------------------|-------|--------------|
| **State** | `PluginState` (күйлер), `PluginLifecycle` (контекст) | Әр күй өзінен қандай әрекет заңды екенін өзі біледі; заңсыз ауысу `IllegalTransitionException` береді. `if/switch` тізбегі жоқ, жаңа күй қосу бар күйлерді өзгертпейді. | R3 | `PluginStateTest`, `PluginManagerTest.illegalTransitionIsRejectedBeforePluginCodeRuns` |
| **Abstract Factory** | `PluginRuntimeFactory`, `V1RuntimeFactory`, `V2RuntimeFactory` | Бір API нұсқасына қажетті объектілер отбасын (плагин объектісі + оған енгізілетін контекст) жасайды. Менеджер нұсқаны `apiVersion` бойынша таңдайды; v3 қосу = жаңа фабрика класы, менеджер өзгермейді. | R4 | `RuntimeFactoryTest` |
| **Adapter** | `LegacyPluginAdapter` | v1 плагині (`init/process/destroy`) өзгертусіз v2 интерфейсі (`start/handle/stop`) ретінде жұмыс істейді. | R4 | `RuntimeFactoryTest.adapterMapsEveryMethodOfV2ToV1`, `PluginManagerTest.startAllActivatesEverythingAndV1PluginWorksThroughAdapter` |
| **Proxy** (қорғаушы) | `FaultBarrierPlugin` + `FaultBarrier` | Хост тек proxy-ді көреді; әр шақыру плагиннің жеке ағынында, timeout-пен орындалады; кез келген `Throwable` ұсталады; timeout-тан кейін плагин карантинге түседі. | R5 | `FaultBarrierTest`, `MaliciousPluginTest` |
| **Facade** | `PluginManager` | Қосымша тек бір класпен жұмыс істейді: `install / start / stop / call / reload / uninstall`. Дескриптор оқу, тәуелділік шешу, ClassLoader, сканер, фабрикалар, fault barrier — бәрі ішінде жасырылған. | бәрі | `PluginManagerTest` |
| **DI** (конструктор арқылы) | `PluginManager` конструкторы, `PluginManager.createDefault` (composition root), `PluginContext` | Менеджер өз тәуелділіктерін өзі жасамайды — конструкторға беріледі (тестте ауыстыруға болады). Плагин хост сервистерін іздемейді — `PluginContext` оған `start(...)` арқылы енгізіледі. | R2, R5 | `PluginManagerTest` (логтарды жинайтын `PluginLogSink` енгізіледі) |

## Қабылданбаған үлгілер

| Үлгі | Неге қабылданбады |
|------|-------------------|
| **Singleton** (`PluginManager` үшін) | Жаһандық күй тесттерді бір-біріне тәуелді етеді; әр тест өз менеджерін жасайды. Бір данаға кепілді composition root (`createDefault`) береді. |
| **Decorator** (fault barrier үшін) | Құрылымы Proxy-ге ұқсас, бірақ мақсаты басқа: біз функционал қоспаймыз, плагинге **қолжетімділікті бақылаймыз** (ағын, timeout, карантин). Сондықтан Proxy. Тізбек (timeout + retry + метрика) керек болса, Decorator-ға өтуге болады. |
| **Observer** (өмірлік цикл оқиғалары) | Қазір күй өзгерісін тыңдайтын ешкім жоқ — YAGNI. Логтар `PluginLogSink` арқылы жеткілікті. |
| **Builder** (`PluginManager` үшін) | Конструкторда 6 параметр, бәрі міндетті; `createDefault` әдетті жағдайды жабады. Builder артық абстракция болар еді. |

Толық негіздемелер: [docs/adr](adr/).

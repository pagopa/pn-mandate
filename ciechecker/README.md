# Esecuzione dei Test CieChecker

## Contesto
Tutti i test JUnit relativi alla verifica della CIE sono organizzati nella directory `ciechecker`.

**Nota importante:**  
Questi test **non vengono eseguiti automaticamente** durante la build Maven standard, poiché il package non rientra nel contesto di test predefinito di Spring Boot.

Per poter eseguire correttamente questi test, è necessario usare il tag JUnit specifico `@Tag("CieChecker")` e una configurazione di esecuzione dedicata.

---

## Regole per i test CieChecker

- Ogni test nel package `ciechecker` **deve essere annotato** con un tag, esempio:

  ```java
  @Tag("CieChecker")

## Configurazione di IntelliJ per eseguire i test con tag
Per creare un'apposita configurazione, seguire i seguenti passaggi:

- Andare su: Run > Edit Configurations...
- Creare una nuova configurazione di tipo JUnit oppure modificarne una esistente.
- Impostare:
  - Test kind: selezionare Tags (di default dovrebbe essere impostato Class)
  - Tags: inserire CieChecker (o quello impostato, dovrà poi essere una configurazione per ogni tag)
  - VM options (opzionale, utile per evitare problemi con JUnit Vintage)
  ```
  -Djunit.jupiter.extensions.autodetection.enabled=true

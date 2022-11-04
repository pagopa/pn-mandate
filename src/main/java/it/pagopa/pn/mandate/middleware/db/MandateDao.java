package it.pagopa.pn.mandate.middleware.db;

import it.pagopa.pn.commons.exceptions.PnInternalException;
import it.pagopa.pn.commons.log.PnAuditLogBuilder;
import it.pagopa.pn.commons.log.PnAuditLogEvent;
import it.pagopa.pn.commons.log.PnAuditLogEventType;
import it.pagopa.pn.mandate.config.PnMandateConfig;
import it.pagopa.pn.mandate.exceptions.PnInvalidVerificationCodeException;
import it.pagopa.pn.mandate.exceptions.PnMandateAlreadyExistsException;
import it.pagopa.pn.mandate.exceptions.PnMandateExceptionCodes;
import it.pagopa.pn.mandate.exceptions.PnMandateNotFoundException;
import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.middleware.db.entities.MandateSupportEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum;
import it.pagopa.pn.mandate.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Repository
@Slf4j
@Import(PnAuditLogBuilder.class)
public class MandateDao extends BaseDao {

    private final PnAuditLogBuilder auditLogBuilder;
    DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient;
    DynamoDbAsyncClient dynamoDbAsyncClient;
    DynamoDbAsyncTable<MandateEntity> mandateTable;
    DynamoDbAsyncTable<MandateSupportEntity> mandateSupportTable;
    DynamoDbAsyncTable<MandateEntity> mandateHistoryTable;
    
    String table;
    

    public MandateDao(DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient,
                       DynamoDbAsyncClient dynamoDbAsyncClient,
                       PnMandateConfig awsConfigs,
                       PnAuditLogBuilder pnAuditLogBuilder) {
        this.mandateTable = dynamoDbEnhancedAsyncClient.table(awsConfigs.getDynamodbTable(), TableSchema.fromBean(MandateEntity.class));
        this.mandateSupportTable = dynamoDbEnhancedAsyncClient.table(awsConfigs.getDynamodbTable(), TableSchema.fromBean(MandateSupportEntity.class));
        this.mandateHistoryTable = dynamoDbEnhancedAsyncClient.table(awsConfigs.getDynamodbTableHistory(), TableSchema.fromBean(MandateEntity.class));
        this.table = awsConfigs.getDynamodbTable();
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
        this.dynamoDbEnhancedAsyncClient = dynamoDbEnhancedAsyncClient;
        this.auditLogBuilder = pnAuditLogBuilder;
    }

    //#region public methods

    /**
     * Ritorna la lista delle deleghe per delegato
     *
     * @param delegateInternaluserid internaluserid del delegato
     * @param status stato da usare nel filtro (OPZIONALE)
     * @return lista delle deleghe
     */
    public Flux<MandateEntity> listMandatesByDelegate(String delegateInternaluserid, Integer status, String mandateId) {
        // devo sempre filtrare. Se lo stato è passato, vuol dire che voglio filtrare solo per quello stato.
        // altrimenti, è IMPLICITO il fatto di filtrare per le deleghe pendenti e attive (ovvero < 20)
        // NB: listMandatesByDelegate e listMandatesByDelegator si assomigliano, ma a livello di query fanno
        // affidamento ad indici diversi e query diverse
        // listMandatesByDelegate si affida all'indice GSI delegate-state, che filtra per utente delegato E stato.
        log.info("listMandatesByDelegate uid={} status={}", delegateInternaluserid, status);

        QueryConditional queryConditional = QueryConditional.sortLessThanOrEqualTo(getKeyBuild(delegateInternaluserid, StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE)));
        if (status != null)
        {
                queryConditional = QueryConditional.keyEqualTo(getKeyBuild(delegateInternaluserid, status));    // si noti keyEqualTo al posto di sortLessThanOrEqualTo
        }        

        // devo sempre mettere un filtro di salvaguardia per quanto riguarda la scadenza della delega.
        // infatti se una delega è scaduta, potrebbe rimanere a sistema per qualche ora/giorno prima di essere svecchiata
        // e quindi va sempre previsto un filtro sulla data di scadenza
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":now", AttributeValue.builder().s(DateUtils.formatDate(ZonedDateTime.now().toInstant())).build());
        String expression = getValidToFilterExpression();

        if (mandateId != null)
        {
            expressionValues.put(":mandateId", AttributeValue.builder().s(mandateId).build());
            expression += "  AND " + getMandateFilterExpression();
        }



        Expression exp = Expression.builder()
            .expression(expression)
            .expressionValues(expressionValues)
            .build();
         
        
        // il filtro cambia in base al fatto se ho chiesto uno stato specifico (uso =)
        //   o se invece non chiedo lo stato (e quindi mi interessano pendenti e attive, uso <=)
        QueryEnhancedRequest qeRequest = QueryEnhancedRequest
                .builder()
                .queryConditional(queryConditional)
                .filterExpression(exp)                
                .scanIndexForward(true)                
                .build();


        // viene volutamente ignorata la gestione della paginazione, che per ora non serve.
        // si suppone infatti che la lista delle deleghe non sia troppo lunga e quindi non vada a sforare il limite di 1MB di paginazione
        return Flux.from(mandateTable.index(GSI_INDEX_DELEGATE_STATE).query(qeRequest)
                .flatMapIterable(Page::items));
    }

    /**
     * Ritorna la lista delle deleghe per delegante
     *
     * @param delegatorInternaluserid internaluserid del delegante
     * @param status stato da usare nel filtr (OPZIONALE)
     * @return lista delle deleghe
     */
    public Flux<MandateEntity> listMandatesByDelegator(String delegatorInternaluserid, Integer status, String mandateId) {
        // devo sempre filtrare. Se lo stato è passato, vuol dire che voglio filtrare solo per quello stato.
        // altrimenti, è IMPLICITO il fatto di filtrare per le deleghe pendenti e attive (ovvero < 20)
        // NB: listMandatesByDelegate e listMandatesByDelegator si assomigliano, ma a livello di query fanno
        // affidamento ad indici diversi e query diverse
        // listMandatesByDelegator si affida all'ordinamento principale, che filtra per utente e delega. Lo stato va previsto a parte nell'expressionfilter
        log.info("listMandatesByDelegator uid={} status={}", delegatorInternaluserid, status);

        int iState = StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE);
        String filterexp = getValidToFilterExpression() + " AND  " + getStatusFilterExpression(true);
        if (status != null)
        {
            filterexp = getValidToFilterExpression() + "  AND " + getStatusFilterExpression(false);   // si noti = status
            iState = status;
        }

        // devo sempre mettere un filtro di salvaguardia per quanto riguarda la scadenza della delega.
        // infatti se una delega è scaduta, potrebbe rimanere a sistema per qualche ora/giorno prima di essere svecchiata
        // e quindi va sempre previsto un filtro sulla data di scadenza
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":now", AttributeValue.builder().s(DateUtils.formatDate(ZonedDateTime.now().toInstant())).build());
        expressionValues.put(":status", AttributeValue.builder().n(iState + "").build());
        if (mandateId != null)
        {
            expressionValues.put(":mandateId", AttributeValue.builder().s(mandateId).build());
            filterexp += " AND " + getMandateFilterExpression();
        }

        Expression exp = Expression.builder()                
        .expression(filterexp)
                .expressionValues(expressionValues)
                .build();    
       

        QueryEnhancedRequest qeRequest = QueryEnhancedRequest
                .builder()
                .queryConditional(QueryConditional.sortBeginsWith(getKeyBuild(delegatorInternaluserid, MandateEntity.MANDATE_PREFIX)))
                .filterExpression(exp)
                .scanIndexForward(true)
                .build();

        return Flux.from(mandateTable.query(qeRequest).items());
    }

    /**
     * Il metodo si occupa di:
     * - leggere l'item dal GSI delegato
     * - validare la richiesta
     * - aggiornarne il contenuto (stato e data accettazione) nell'entity
     * - creare un NUOVO record di supporto, con TTL pari a scadenza della delega (se è prevista). Questo record, quando scadrà, darà luogo ad un loopback
     *   che mi permetterà di spostare il record principale nello storico. Il TTL NON viene messo nel record principale perchè se qualcosa va storto almeno
     *   il record principale rimane (scaduto) e non viene perso.
     * - update dell'entity aggiornata in DB
     *
     * @param delegateInternaluserid internaluserid del delegato
     * @param mandateId id della delega
     * @param verificationCode codice di verifica della relativo all'accettazione
     * @return void
     */
    public Mono<Void> acceptMandate(String delegateInternaluserid, String mandateId, String verificationCode)
    {
        String logMessage = String.format("acceptMandate for delegate uid=%s mandateid=%s verificationCode=%s", delegateInternaluserid, mandateId, verificationCode); 
        PnAuditLogEvent logEvent = auditLogBuilder
                .before(PnAuditLogEventType.AUD_DL_ACCEPT, logMessage)
                .uid(delegateInternaluserid)
                .build();

        logEvent.log();
        return retrieveMandateForDelegate(delegateInternaluserid, mandateId)
                .switchIfEmpty(Mono.error(new PnMandateNotFoundException()))
                .flatMap(mandate -> {
                    if (!mandate.getValidationcode().equals(verificationCode))
                        throw new PnInvalidVerificationCodeException();

                    log.info("retrieved mandateobj={}", mandate);
                    if (mandate.getState() == StatusEnumMapper.intValfromStatus(StatusEnum.PENDING))
                    {
                        // aggiorno lo stato, solo se era in pending. NB: non do errore
                        mandate.setAccepted(Instant.now());
                        mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE));
                    }
                    else if (mandate.getState() == StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE))
                        log.info("mandate is already ACTIVE accepting silently");
                    else
                    {
                        // non dovrebbe veramente succedere, perchè vuol dire che è rimasta una delega scaduta e che qualcuno ci ha pure chiesto l'accettazione, cmq tiro eccezione
                        log.warn("mandate is not PENDING or ACTIVE, throw error");
                        throw new PnInternalException("accept a expired mandate is not permitted", PnMandateExceptionCodes.ERROR_CODE_MANDATE_NOTACCEPTABLE);
                    }
                    // Se la delega prevede una scadenza impostata dall'utente, creo un record di supporto con TTL
                    // e quando questo verrà cancellato, dynamoDB invocherà la nostra logica che andrà a spostare il record principale nello storico.
                    // Questo perchè se mettevo il TTL nel record principale e per qualche anomalia non veniva gestito l'evento di cancellazione
                    // avrei perso definitivamente il record della delega (scaduta, ma che va mantenuta per 10 anni nello storico)
                    TransactWriteItemsEnhancedRequest.Builder transactionBuilder = TransactWriteItemsEnhancedRequest.builder();
                    if (mandate.getValidto() != null) {
                        MandateSupportEntity support = new MandateSupportEntity(mandate);
                        transactionBuilder.addPutItem(mandateSupportTable, TransactPutItemEnhancedRequest.builder(MandateSupportEntity.class).item(support).build());
                        log.info("mandate has validto setted, creating also support entity for ttl expiration");
                    }

                    // aggiungo l'update delle deleghe e lancio la transazione
                    TransactWriteItemsEnhancedRequest transaction = transactionBuilder
                            .addUpdateItem(mandateTable, TransactUpdateItemEnhancedRequest.builder(MandateEntity.class).item(mandate).ignoreNulls(true).build())
                            .build();
                    
                    return Mono.fromFuture(dynamoDbEnhancedAsyncClient.transactWriteItems(transaction).thenApply(x -> {
                        String messageAction = String.format(
                                "mandate accepted delegator uid=%s delegate uid=%s mandateobj=%S",
                                mandate.getDelegator(), mandate.getDelegate(), mandate);
                        logEvent.generateSuccess(messageAction).log();
                        return mandate;
                    })).then();
                })
                .onErrorResume(throwable -> {
                    logEvent.generateFailure(throwable.getMessage()).log();
                    return Mono.error(throwable);
                });

    }

    /**
     *  Il metodo si occupa di:
     *  - leggere l'item dal GSI delegato
     *  - aggiornarne il contenuto (stato e data rifiuto) nell'entity
     *  - creare una copia dell'entity nella tabella dello storico, impostandone il TTL a 10 anni
     *  - eliminare l'entity dalla tabella principale
     *
     * @param delegateInternaluserid internaluserid del delegato
     * @param mandateId id della delega
     * @return void
     */
    public Mono<Void> rejectMandate(final String delegateInternaluserid, final String mandateId)
    {
        // qui l'internaluserid è quello del DELEGATO, e quindi NON posso usare direttamente l'informazione per accedere al record.
        // devo passare quindi per l'indice sul delegato, recuperarmi la riga, aggiornare il contenuto e salvarla.
        // NB: si noti che ci può essere un problema legato alla concorrenza, ma dato che i dati sulle deleghe non cambiano così frequentemente, 
        // si accetta la possibilità dell'improbabilità che arrivino 2 scritture contemporanee nel piccolo lasso di tempo tra query e update...

        String logMessage = String.format("rejectMandate for delegate uid=%s mandateid=%s", delegateInternaluserid, mandateId);
        PnAuditLogEvent logEvent = auditLogBuilder
                .before(PnAuditLogEventType.AUD_DL_REJECT, logMessage)
                .uid(delegateInternaluserid)
                .build();
        logEvent.log();

        return retrieveMandateForDelegate(delegateInternaluserid, mandateId)
                .switchIfEmpty(Mono.error(new PnMandateNotFoundException()))
                .flatMap(mandate -> {
                    log.info("rejectMandate mandate for delegate retrieved mandateobj={}", mandate);

                    if (mandate.getState() == StatusEnumMapper.intValfromStatus(StatusEnum.PENDING)
                            || mandate.getState() == StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE))
                    {
                        // aggiorno lo stato, solo se era in pending o active, ignoro eventuali altri stati (che NON dovrebbero essere presenti)
                        mandate.setRejected(Instant.now());
                        mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.REJECTED));

                        return Mono.fromFuture(saveHistoryAndDeleteFromMain(mandate))
                                .map(m -> {
                                    logEvent.generateSuccess("mandate rejected mandate={}", m).log();
                                    return m;
                                }).then();
                    }
                    else
                        log.warn("no mandate found in pending,active or rejected state, fail silently");

                    return Mono.empty();
                })
                .onErrorResume(throwable -> {
                    logEvent.generateFailure(throwable.getMessage()).log();
                    return Mono.error(throwable);
                });

    }

    /**
     * Il metodo si occupa di:
     * - leggere l'item dalla tabella principale
     * - aggiornarne il contenuto (stato e data revoca) nell'entity
     * - creare una copia dell'entity nella tabella dello storico, impostandone il TTL a 10 anni
     * - eliminare l'entity dalla tabella principale
     * - eliminare eventuale entity di supporto dalla tabella principale
     *
     * @param delegatorInternaluserid internaluserid del delegante
     * @param mandateId id della delega
     * @return void
     */
    public Mono<Object> revokeMandate(String delegatorInternaluserid, String mandateId)
    {
        String logMessage = String.format("revokeMandate for delegate uid=%s mandateid=%s", delegatorInternaluserid, mandateId);
        PnAuditLogEvent logEvent = auditLogBuilder
                .before(PnAuditLogEventType.AUD_DL_REVOKE, logMessage)
                .uid(delegatorInternaluserid)
                .build();

        logEvent.log();
        return Mono.fromFuture(retrieveMandateForDelegator(delegatorInternaluserid, mandateId)
                .thenCompose(mandate -> {
                            if (mandate == null)
                            {
                                logEvent.generateFailure(String.format("revokeMandate skipped, mandate not found mandateid=%s", mandateId)).log();
                                return CompletableFuture.completedFuture(mandate);
                            }

                            log.info("revokeMandate mandate for delegate retrieved mandateobj={}", mandate);
                            if (mandate.getState() == StatusEnumMapper.intValfromStatus(StatusEnum.PENDING)
                                    || mandate.getState() == StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE))
                            {
                                // aggiorno lo stato, solo se era in pending o active, ignoro eventuali altri stati (che NON dovrebbero essere presenti)
                                mandate.setRevoked(Instant.now());
                                mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.REVOKED));
                                return saveHistoryAndDeleteFromMain(mandate);
                            }
                            return CompletableFuture.completedFuture(mandate);
                        })
                )
                .onErrorResume(throwable -> {
                    logEvent.generateFailure(throwable.getMessage()).log();
                    return Mono.error(throwable);
                })
                .map(m -> {
                    logEvent.generateSuccess("mandate revoked mandate={}", m).log();
                    return m;
                });
    }

    /**
     *  Il metodo si occupa di:
     *  - leggere l'item dalla tabella principale
     *  - aggiornarne il contenuto (stato) nell'entity
     *  - creare una copia dell'entity nella tabella dello storico, impostandone il TTL a 10 anni
     *  - eliminare l'entity dalla tabella principale
     *  - eliminare eventuale entity di supporto dalla tabella principale
     *
     * @param delegatorInternaluserid internaluserid del delegante
     * @param mandateId id della delega
     * @return void
     */
    public Mono<Object> expireMandate(String delegatorInternaluserid, String mandateId)
    {
        String logMessage = String.format("expireMandate for delegate uid=%s{} mandateid=%s", delegatorInternaluserid, mandateId);
        PnAuditLogEvent logEvent = auditLogBuilder
                .before(PnAuditLogEventType.AUD_DL_EXPIRE, logMessage)
                .uid(delegatorInternaluserid)
                .build();

        logEvent.log();
        return Mono.fromFuture(retrieveMandateForDelegator(delegatorInternaluserid, mandateId)
                .thenCompose(mandate -> {
                            if (mandate == null) {
                                throw new PnMandateNotFoundException();
                            }
                            log.info("expireMandate mandate for delegate retrieved mandateobj={}", mandate);
                            mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.EXPIRED));
                            return saveHistoryAndDeleteFromMain(mandate);
                        })
                )
                .onErrorResume(throwable -> {
                    logEvent.generateFailure(throwable.getMessage()).log();
                    return Mono.error(throwable);
                })
                .map(m -> {
                    logEvent.generateSuccess("mandate expired mandate={}", m).log();
                    return m;
                });
    }

    /**
     * Il metodo si occupa di:
     * - controllare che non siano presenti deleghe per la coppia delegante-delegato
     * - creare la delega nella tabella mandate
     *
     * @param mandate oggetto delega da creare
     * @return oggetto delega creato
     */
    public Mono<MandateEntity> createMandate(MandateEntity mandate){
        String logMessage = String.format("create mandate mandate=%s", mandate);
        PnAuditLogEvent logEvent = auditLogBuilder
                .before(PnAuditLogEventType.AUD_DL_CREATE, logMessage)
                .uid(mandate.getDelegator())
                .build();

        logEvent.log();

        return Mono.fromFuture(
                        countMandateForDelegateAndDelegator(mandate.getDelegator(), mandate.getDelegate())
                .thenCompose(total -> {
                    if (total == 0)
                    {
                        log.info("no current mandate for delegator-delegate pair, can proceed to create mandate");
                        PutItemEnhancedRequest<MandateEntity> putRequest = PutItemEnhancedRequest.builder(MandateEntity.class)
                                .item(mandate)
                                //.conditionExpression()
                                .build();
                        return mandateTable.putItem(putRequest).thenApply(x -> {
                            log.info("saved mandate mandateobj={}", mandate);
                            return mandate;
                        });
                    }
                    else {
                        throw new PnMandateAlreadyExistsException();
                    }
                }))
                .onErrorResume(throwable -> {
                    logEvent.generateFailure(throwable.getMessage()).log();
                    return Mono.error(throwable);
                })
                .map(mandateCreated -> {
                    log.info("created mandate mandateobj={}", mandateCreated);
                    logEvent.generateSuccess(String.format("created mandate mandateobj=%s", mandateCreated)).log();

                    return mandateCreated;
                });
    }

    //#endregion

    //#region private methods

    /**
     * Recupera una delega in base all'internaluserid del delegante e all'id della delega
     *
     * @param delegatorInternaluserid internaluserid del delegante
     * @param mandateId id della delega
     * @return future contenente la delega
     */
    private CompletableFuture<MandateEntity> retrieveMandateForDelegator(String delegatorInternaluserid, String mandateId) {
        MandateEntity mandate = new MandateEntity(delegatorInternaluserid, mandateId);
        // qui l'internaluserid è quello del DELEGANTE, e quindi posso usare direttamente l'informazione per accedere al record
        GetItemEnhancedRequest getitemRequest = GetItemEnhancedRequest.builder()
        .key(getKeyBuild(mandate.getDelegator() , mandate.getSk()))
        .build();


        return mandateTable.getItem(getitemRequest);
    }

    /**
     * Recupera una delega in base all'internaluserid del delegato e all'id della delega
     * @param delegateInternaluserid internaluserid del delegato
     * @param mandateId id della delega
     * @return publisher contenente un solo record di delega
     */
    private Mono<MandateEntity> retrieveMandateForDelegate(String delegateInternaluserid, String mandateId) {
        // qui l'internaluserid è quello del DELEGATO, e quindi NON posso usare direttamente l'informazione per accedere al record.
        // devo passare quindi per l'indice sul delegato, recuperarmi la riga, aggiornare il contenuto e salvarla.
        // NB: si noti che ci può essere un problema legato alla concorrenza, ma dato che i dati sulle deleghe non cambiano così frequentemente, 
        // si accetta la possibilità dell'improbabilità che arrivino 2 scritture contemporanee nel piccolo lasso di tempo tra query e update...


        // uso l'expression filter per filtrare per mandateid, dato che su questo indice non fa parte della key.
        // si accetta il costo di leggere tutte le righe per un delegato per poi tornarne una
        // non viene filtrato lo stato, dato che questo metodo può essere usato per motivi generici
        MandateEntity mandate = new MandateEntity(delegateInternaluserid, mandateId);
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":mandateid",  AttributeValue.builder().s(mandate.getSk()).build());

        Expression exp = Expression.builder()
                .expression(MandateEntity.COL_SK + " = :mandateid")
                .expressionValues(expressionValues)
                .build();    

        QueryEnhancedRequest qeRequest = QueryEnhancedRequest
                .builder()
                .queryConditional(QueryConditional.keyEqualTo(getKeyBuild(delegateInternaluserid)))
                .filterExpression(exp)                
                .scanIndexForward(true)                
                .build();

        return Flux.from(mandateTable.index(GSI_INDEX_DELEGATE_STATE).query(qeRequest)
                .flatMapIterable(Page::items).limit(1)).take(1).next();
    }

    /**
     * Recupera il numero di deleghe presenti per la coppia delegante-delegato
     *
     * @param delegatorInternaluserid internaluserid del delegante
     * @param delegateInternaluserid internaluserid del delegato
     * @return future contenente il conteggio delle deleghe
     */
    private CompletableFuture<Integer> countMandateForDelegateAndDelegator(String delegatorInternaluserid, String delegateInternaluserid) {
        // qui ho entrambi gli id, mi serve sapere se ho già una delega per la coppia delegante-delegato, in pending/attiva e non scaduta ovviamente.

        // uso l'expression filter per filtrare le deleghe valide per il delegato
        // si accetta il costo di leggere più righe per niente
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":delegate",  AttributeValue.builder().s(delegateInternaluserid).build());
        expressionValues.put(":delegator",  AttributeValue.builder().s(delegatorInternaluserid).build());
        expressionValues.put(":mandateprefix",  AttributeValue.builder().s(MandateEntity.MANDATE_PREFIX).build());
        expressionValues.put(":now", AttributeValue.builder().s(DateUtils.formatDate(ZonedDateTime.now().toInstant())).build());
        expressionValues.put(":status", AttributeValue.builder().n(StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE) + "").build());

        QueryRequest qeRequest = QueryRequest
                .builder()
                .select(Select.COUNT)
                .tableName(table)
                .filterExpression(getValidToFilterExpression() + " AND " + getStatusFilterExpression(true) + " AND (" + MandateEntity.COL_S_DELEGATE + " = :delegate)")
                .keyConditionExpression(MandateEntity.COL_PK + " = :delegator AND begins_with(" + MandateEntity.COL_SK + ", :mandateprefix)")
                .expressionAttributeValues(expressionValues)
                .build();

        return dynamoDbAsyncClient.query(qeRequest).thenApply(QueryResponse::count);
    }


    private CompletableFuture<MandateEntity> saveHistoryAndDeleteFromMain(MandateEntity mandate)
    {
        // aggiorno il TTL
        mandate.setTtl(LocalDateTime.now().plusYears(10).atZone(ZoneId.systemDefault()).toEpochSecond());

        MandateSupportEntity mandateSupport = new MandateSupportEntity(mandate);

        TransactWriteItemsEnhancedRequest transaction = TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(mandateHistoryTable, TransactPutItemEnhancedRequest.builder(MandateEntity.class).item(mandate).build())
                .addDeleteItem(mandateTable, TransactDeleteItemEnhancedRequest.builder().key(getKeyBuild(mandate.getDelegator(), mandate.getSk())).build())
                .addDeleteItem(mandateSupportTable, TransactDeleteItemEnhancedRequest.builder().key(getKeyBuild(mandateSupport.getDelegator(), mandateSupport.getSk())).build())
                .build();

        return dynamoDbEnhancedAsyncClient.transactWriteItems(transaction).thenApply(x -> {
            if (log.isInfoEnabled())
                log.info("mandate saved in history and deleted from main table mandateobj={}", mandate);

            return mandate;
        });
    }

    private String getValidToFilterExpression(){
        return "(" + MandateEntity.COL_D_VALIDTO + " > :now OR attribute_not_exists(" + MandateEntity.COL_D_VALIDTO + ")) ";
    }

    private String getStatusFilterExpression(boolean lessEqualThan){
        return " (" + MandateEntity.COL_I_STATE + " " + (lessEqualThan?"<=":"=") + " :status) ";
    }

    private String getMandateFilterExpression() {
        return "(" + MandateEntity.COL_S_MANDATEID + " = :mandateId) ";
    }
    //#endregion
}

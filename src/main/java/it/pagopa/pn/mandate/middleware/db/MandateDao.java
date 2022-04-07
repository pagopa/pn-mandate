package it.pagopa.pn.mandate.middleware.db;

import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum;
import it.pagopa.pn.mandate.rest.utils.InvalidVerificationCodeException;
import it.pagopa.pn.mandate.rest.utils.MandateNotFoundException;
import it.pagopa.pn.mandate.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Repository
@Slf4j
public class MandateDao extends BaseDao {

    private static final String MANDATE_PREFIX = "MANDATE-";
    private static final String MANDATE_TRIGGERHELPER_PREFIX = "MANDATETRIGGERHELPER-";
    private final UserDao userDao;
    //public static final String DELEGATOR_PREFIX = "USERDELEGATOR-";


    DynamoDbAsyncTable<MandateEntity> mandateTable;
    DynamoDbAsyncTable<MandateEntity> mandateHistoryTable;


    public MandateDao(DynamoDbEnhancedAsyncClient dynamoDbAsyncClient,
                       @Value("${aws.dynamodb.table}") String table,
                       @Value("${aws.dynamodb.table_history}") String tableHistory,
                       UserDao userDao) {
        this.mandateTable = dynamoDbAsyncClient.table(table, TableSchema.fromBean(MandateEntity.class));            
        this.mandateHistoryTable = dynamoDbAsyncClient.table(tableHistory, TableSchema.fromBean(MandateEntity.class));
        this.userDao = userDao;
    }

    //#region public methods

    public Flux<MandateEntity> listMandatesByDelegate(String delegate_internaluserid, Integer status) {
        // devo sempre filtrare. Se lo stato è passato, vuol dire che voglio filtrare solo per quello stato.
        // altrimenti, è IMPLICITO il fatto di filtrare per le deleghe pendenti e attive (ovvero < 20)
        // NB: listMandatesByDelegate e listMandatesByDelegator si assomigliano, ma a livello di query fanno
        // affidamento ad indici diversi e query diverse
        // listMandatesByDelegate si affida all'indice GSI delegate-state, che filtra per utente delegato E stato.
        
        int i_state = StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE);
        if (status != null)
        {
                i_state = status;
        }        

        // devo sempre mettere un filtro di salvaguardia per quanto riguarda la scadenza della delega.
        // infatti se una delega è scaduta, potrebbe rimanere a sistema per qualche ora/giorno prima di essere svecchiata
        // e quindi va sempre previsto un filtro sulla data di scadenza
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":now", AttributeValue.builder().s(DateUtils.formatDate(ZonedDateTime.now())).build());

        Expression exp = Expression.builder()
        .expression(MandateEntity.COL_D_VALIDTO + " > :now")
                .expressionValues(expressionValues)
                .build();    
         
        
        // il filtro cambia in base al fatto se ho chiesto uno stato specifico (uso =)
        //   o se invece non chiedo lo stato (e quindi mi interessano pendenti e attive, uso <=)
        QueryEnhancedRequest qeRequest = QueryEnhancedRequest
                .builder()
                .queryConditional(
                        status!=null?QueryConditional.keyEqualTo(getKeyBuild(delegate_internaluserid, i_state)):
                                        QueryConditional.sortLessThanOrEqualTo(getKeyBuild(delegate_internaluserid, i_state)))
                .filterExpression(exp)                
                .scanIndexForward(true)                
                .build();

        // viene volutamente ignorata la gestione della paginazione, che per ora non serve.
        // si suppone infatti che la lista delle deleghe non sia troppo lunga e quindi non vada a sforare il limite di 1MB di paginazione
        return Flux.from(mandateTable.index(GSI_INDEX_DELEGATE_STATE).query(qeRequest)
                .flatMapIterable(Page::items).map(this::normalizeAfterReadFromDb));
                //.flatMap(item -> Mono.just(normalizeAfterReadFromDb(item)));
    }

    public Flux<MandateEntity> listMandatesByDelegator(String delegator_internaluserid, Integer status) {
        // devo sempre filtrare. Se lo stato è passato, vuol dire che voglio filtrare solo per quello stato.
        // altrimenti, è IMPLICITO il fatto di filtrare per le deleghe pendenti e attive (ovvero < 20)
        // NB: listMandatesByDelegate e listMandatesByDelegator si assomigliano, ma a livello di query fanno
        // affidamento ad indici diversi e query diverse
        // listMandatesByDelegator si affida all'ordinamento principale, che filtra per utente e delega. Lo stato va previsto a parte nell'expressionfilter
        
        int i_state = StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE);
        if (status != null)
        {
                i_state = status;
        }        

        // devo sempre mettere un filtro di salvaguardia per quanto riguarda la scadenza della delega.
        // infatti se una delega è scaduta, potrebbe rimanere a sistema per qualche ora/giorno prima di essere svecchiata
        // e quindi va sempre previsto un filtro sulla data di scadenza
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":now", AttributeValue.builder().s(DateUtils.formatDate(ZonedDateTime.now())).build());
        expressionValues.put(":status", AttributeValue.builder().n(i_state + "").build());

        Expression exp = Expression.builder()                
        .expression(status != null?"(" + MandateEntity.COL_D_VALIDTO + " > :now) AND (" + MandateEntity.COL_I_STATE + " = :status)":
                "(" + MandateEntity.COL_D_VALIDTO + " > :now) AND (" + MandateEntity.COL_I_STATE + " <= :status)")
                .expressionValues(expressionValues)
                .build();    
       

        QueryEnhancedRequest qeRequest = QueryEnhancedRequest
                .builder()
                .queryConditional(QueryConditional.sortBeginsWith(getKeyBuild(delegator_internaluserid, MANDATE_PREFIX)))
                .filterExpression(exp)
                .scanIndexForward(true)
                .build();

        return Flux.from(mandateTable.query(qeRequest).items().map(this::normalizeAfterReadFromDb));
                //.flatMap(item -> Mono.just(normalizeAfterReadFromDb(item)));
    }

    public Mono<Object> acceptMandate(String delegate_internaluserid, String mandateId, String verificationCode)
    {
        // Il metodo deve:
        // - leggere l'item dal GSI delegato
        // - validare la richiesta
        // - aggiornarne il contenuto (stato e data accettazione) nell'entity
        // - creare un NUOVO record fantasma, con TTL pari a scadenza della delega (se è prevista). Questo record, quando scadrà, darà luogo ad un loopback 
        //   che mi permetterà di spostare il record principale nello storico. Il TTL NON viene messo nel record principale perchè se qualcosa va storto almeno
        //   il record principale rimane (scaduto) e non viene perso.
        // - update dell'entity aggiornata in DB
        log.info("accepting mandate for delegate uid:{} mandateid:{}", delegate_internaluserid, mandateId);
        return Mono.fromFuture(() -> retrieveMandateForDelegate(delegate_internaluserid, mandateId)
                .subscribe(mandate -> {
                        if (mandate == null)
                                throw new MandateNotFoundException();
        
                        if (!mandate.getValidationcode().equals(verificationCode))
                                throw new InvalidVerificationCodeException();
                        
                        if (mandate.getState() == StatusEnumMapper.intValfromStatus(StatusEnum.PENDING))
                        {
                                // aggiorno lo stato, solo se era in pending. NB: non do errore
                                mandate.setAccepted(DateUtils.formatTime(ZonedDateTime.now()));
                                mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE));
                        }

                    try {
                        // Se la delega prevede una scadenza impostata dall'utente, creo un record di supporto con TTL
                        // e quando questo verrà cancellato, dynamoDB invocherà la nostra logica che andrà a spostare il record principale nello storico.
                        // Questo perchè se mettevo il TTL nel record principale e per qualche anomalia non veniva gestito l'evento di cancellazione
                        // avrei perso definitivamente il record della delega (scaduta, ma che va mantenuta per 10 anni nello storico)
                        if (mandate.getValidto() != null && !mandate.getValidto().equals("")) {
                            MandateEntity support = new MandateEntity();
                            support.setDelegator(mandate.getDelegator());
                            support.setId(MANDATE_TRIGGERHELPER_PREFIX + mandate.getId().replace(MANDATE_PREFIX, ""));
                            long ttlexpiretimestamp = DateUtils.parseDate(mandate.getValidto()).toEpochSecond();
                            support.setTtl(ttlexpiretimestamp);

                            PutItemEnhancedRequest<MandateEntity> puReq = PutItemEnhancedRequest.builder(MandateEntity.class)
                                    .item(support)
                                    .returnValues(ReturnValue.ALL_OLD)
                                    .build();

                            mandateTable.putItem(puReq).thenApply(x -> {
                                return mandate;
                            }).thenCompose(m -> {
                                // aggiorno il record principale
                                UpdateItemEnhancedRequest<MandateEntity> updRequest = UpdateItemEnhancedRequest.builder(MandateEntity.class)
                                        .item(m)
                                        .ignoreNulls(true)
                                        .build();
                                return mandateTable.updateItem(updRequest);
                            }).completeOnTimeout(mandate, 10000, TimeUnit.MILLISECONDS).get();
                        } else {
                            // aggiorno il record principale
                            UpdateItemEnhancedRequest<MandateEntity> updRequest = UpdateItemEnhancedRequest.builder(MandateEntity.class)
                                    .item(mandate)
                                    .ignoreNulls(true)
                                    .build();
                            mandateTable.updateItem(updRequest).completeOnTimeout(mandate, 10000, TimeUnit.MILLISECONDS).get();
                        }
                    } catch (Exception e) {
                        log.error("Cannot complete reject", e);
                        throw new RuntimeException(e);
                    }
                })
                .thenApply(x -> {
                    log.info("accepted mandate for delegate uid:{} mandateid:{} DONE", delegate_internaluserid, mandateId);
                    return "DONE";
                }))
                .zipWhen(r -> userDao.updateUserPendingCount(delegate_internaluserid)
                        ,(r, u) -> r);

    }

    public Mono<Object> rejectMandate(final String delegate_internaluserid, final String mandateId)
    {
        // qui l'internaluserid è quello del DELEGATO, e quindi NON posso usare direttamente l'informazione per accedere al record.
        // devo passare quindi per l'indice sul delegato, recuperarmi la riga, aggiornare il contenuto e salvarla.
        // NB: si noti che ci può essere un problema legato alla concorrenza, ma dato che i dati sulle deleghe non cambiano così frequentemente, 
        // si accetta la possibilità dell'improbabilità che arrivino 2 scritture contemporanee nel piccolo lasso di tempo tra query e update...
        
        // Il metodo deve:
        // - leggere l'item dal GSI delegato
        // - aggiornarne il contenuto (stato e data rifiuto) nell'entity
        // - creare una copia dell'entity nella tabella dello storico, impostandone il TTL a 10 anni
        // - eliminare l'entity dalla tabella principale
        log.info("rejecting mandate for delegate uid:{} mandateid:{}", delegate_internaluserid, mandateId);
        return Mono.fromFuture(() -> retrieveMandateForDelegate(delegate_internaluserid, mandateId)
                .subscribe(mandate -> {
                    log.info("mandate for delegate retrieved mandateobj:{}", mandate);
                    if (mandate == null)
                        throw new MandateNotFoundException();

                    if (mandate.getState() == StatusEnumMapper.intValfromStatus(StatusEnum.PENDING)
                            || mandate.getState() == StatusEnumMapper.intValfromStatus(StatusEnum.REJECTED))
                    {
                        // aggiorno lo stato, solo se era in pending. NB: non do errore
                        mandate.setRejected(DateUtils.formatTime(ZonedDateTime.now()));
                        mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.REJECTED));
                    }

                    mandate.setTtl(ZonedDateTime.now().plusYears(10).toEpochSecond());
                    try {
                        mandateHistoryTable.putItem(mandate).thenApply(x -> {
                            log.info("mandate saved in history mandateid:{}", mandate.getId());
                            return mandate;
                        }).thenCompose(mandate_rejected -> {
                            // elimino il record principale
                            DeleteItemEnhancedRequest delRequest = DeleteItemEnhancedRequest.builder()
                                    .key(getKeyBuild(mandate_rejected.getDelegator(), MANDATE_PREFIX + mandateId))
                                    .build();

                            return mandateTable.deleteItem(delRequest).thenApply(x -> {
                                log.info("mandate deleted from main table mandateid:{}", mandate_rejected.getId());
                                return mandate_rejected;
                            });
                        }).thenCompose(mandate_rejected -> {
                            // elimino l'eventuale record di supporto (se non c'è non mi interessa)
                            DeleteItemEnhancedRequest del1Request = DeleteItemEnhancedRequest.builder()
                                    .key(getKeyBuild(mandate_rejected.getDelegator(), MANDATE_TRIGGERHELPER_PREFIX + mandateId))
                                    .build();

                            return mandateTable.deleteItem(del1Request).thenApply(mandhelper -> {
                                log.info("mandate (helper) deleted from main table mandateid:{} mandatehelperobj:{}", mandate_rejected.getId(), mandhelper);
                                return mandate_rejected;
                            });
                        }).completeOnTimeout(mandate, 10000, TimeUnit.MILLISECONDS).get();
                    } catch (Exception e) {
                       log.error("Cannot complete reject", e);
                       throw new RuntimeException(e);
                    }
                })
                .thenApply(x -> {
                    log.info("rejecting mandate for delegate uid:{} mandateid:{} DONE", delegate_internaluserid, mandateId);
                    return "DONE";
                }))
                .zipWhen(r -> userDao.updateUserPendingCount(delegate_internaluserid)
                ,(r, u) -> r);
    } 

    public Mono<Object> revokeMandate(String delegator_internaluserid, String mandateId)
    {
        // Il metodo deve:
        // - leggere l'item dalla tabella principale
        // - aggiornarne il contenuto (stato e data revoca) nell'entity
        // - creare una copia dell'entity nella tabella dello storico, impostandone il TTL a 10 anni
        // - eliminare l'entity dalla tabella principale
        // - eliminare eventuale entity di supporto dalla tabella principale

        return Mono.fromFuture(() -> retrieveMandateForDelegator(delegator_internaluserid, mandateId)
                .thenApply(mandate -> {
                        // aggiorno lo stato
                        mandate.setRevoked(DateUtils.formatTime(ZonedDateTime.now()));
                        mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.REVOKED));
                        return mandate;
                })
                .thenApply(mandate_rovoked -> {
                        //salvo nello storico
                        mandate_rovoked.setTtl(LocalDateTime.now().plusYears(10).atZone(ZoneId.systemDefault()).toEpochSecond());
                        return mandate_rovoked;
                })
                .thenCompose(mandate_rovoked -> mandateHistoryTable.putItem(mandate_rovoked).exceptionally(ex -> {
                    log.error("errore salvataggio storico", ex);
                    throw new RuntimeException(ex);
                }).thenApply(x -> {
                    return  mandate_rovoked;
                }))

                .thenCompose(mandate_rovoked -> {
                    // elimino il record principale
                    DeleteItemEnhancedRequest delRequest = DeleteItemEnhancedRequest.builder()
                            .key(getKeyBuild(delegator_internaluserid, MANDATE_PREFIX + mandateId))
                            .build();

                    return mandateTable.deleteItem(delRequest).thenApply(x -> {
                        return mandate_rovoked;});

                })
                .thenCompose(mandate_deleted -> {
                    // elimino l'eventuale record di supporto (se non c'è non mi interessa)
                    DeleteItemEnhancedRequest delhelpRequest = DeleteItemEnhancedRequest.builder()
                            .key(getKeyBuild(delegator_internaluserid, MANDATE_TRIGGERHELPER_PREFIX + mandateId))
                            .build();

                    return mandateTable.deleteItem(delhelpRequest).thenApply(x -> mandate_deleted);
                })
                )
                .flatMap(mandate_rovoked -> {
                    return userDao.updateUserPendingCount(mandate_rovoked.getDelegate())
                            .then(Mono.just(mandate_rovoked));
                });



         
    } 
 
    public Mono<MandateEntity> createMandate(MandateEntity mandate){
        PutItemEnhancedRequest<MandateEntity> putRequest = PutItemEnhancedRequest.builder(MandateEntity.class)
                .item(normalizeBeforeWriteInDb(mandate))
                //.conditionExpression()
                .build();

        return Mono.fromFuture(mandateTable.putItem(putRequest))
                .then(Mono.just(normalizeAfterReadFromDb(mandate)))
                .flatMap(mandate_created -> {
                    return userDao.updateUserPendingCount(mandate_created.getDelegate())
                            .then(Mono.just(mandate_created));
                });
    }

    //#endregion

    //#region private methods

    private CompletableFuture<MandateEntity> retrieveMandateForDelegator(String internaluserid, String mandateId) {
        // qui l'internaluserid è quello del DELEGANTE, e quindi posso usare direttamente l'informazione per accedere al record
        GetItemEnhancedRequest getitemRequest = GetItemEnhancedRequest.builder()
        .key(getKeyBuild(internaluserid, MANDATE_PREFIX + mandateId))
        .build();


        return mandateTable.getItem(getitemRequest);
    }
 
    private SdkPublisher<MandateEntity> retrieveMandateForDelegate(String internaluserid, String mandateId) {
        // qui l'internaluserid è quello del DELEGATO, e quindi NON posso usare direttamente l'informazione per accedere al record.
        // devo passare quindi per l'indice sul delegato, recuperarmi la riga, aggiornare il contenuto e salvarla.
        // NB: si noti che ci può essere un problema legato alla concorrenza, ma dato che i dati sulle deleghe non cambiano così frequentemente, 
        // si accetta la possibilità dell'improbabilità che arrivino 2 scritture contemporanee nel piccolo lasso di tempo tra query e update...


        // uso l'expression filter per filtrare per mandateid, dato che su questo indice non fa parte della key.
        // si accetta il costo di leggere tutte le righe per un delegato per poi tornarne una
        // non viene filtrato lo stato, dato che questo metodo può essere usato per motivi generici
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":mandateid",  AttributeValue.builder().s(MANDATE_PREFIX + mandateId).build());

        Expression exp = Expression.builder()
                .expression("sk = :mandateid")
                .expressionValues(expressionValues)
                .build();    

        QueryEnhancedRequest qeRequest = QueryEnhancedRequest
                .builder()
                .queryConditional(QueryConditional.keyEqualTo(getKeyBuild(internaluserid)))
                .filterExpression(exp)                
                .scanIndexForward(true)                
                .build();

        return mandateTable.index(GSI_INDEX_DELEGATE_STATE).query(qeRequest)
                .flatMapIterable(Page::items).limit(1);
    }


    private MandateEntity normalizeBeforeWriteInDb(MandateEntity ent)
    {
        if (ent.getId() != null && !ent.getId().startsWith(MANDATE_PREFIX))
                ent.setId(MANDATE_PREFIX + ent.getId());
        
        return ent;
    }

    
    private MandateEntity normalizeAfterReadFromDb(MandateEntity ent)
    {
        if (ent.getId() != null && ent.getId().startsWith(MANDATE_PREFIX))
                ent.setId(ent.getId().replace(MANDATE_PREFIX, ""));
        
        return ent;
    }
    //#endregion
}

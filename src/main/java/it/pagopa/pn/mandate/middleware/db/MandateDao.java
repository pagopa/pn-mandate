package it.pagopa.pn.mandate.middleware.db;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import it.pagopa.pn.mandate.mapper.StatusEnumMapper;
import it.pagopa.pn.mandate.middleware.db.entities.MandateEntity;
import it.pagopa.pn.mandate.rest.mandate.v1.dto.MandateDto.StatusEnum;
import it.pagopa.pn.mandate.rest.utils.InvalidVerificationCodeException;
import it.pagopa.pn.mandate.rest.utils.MandateNotFoundException;
import it.pagopa.pn.mandate.utils.DateUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@Repository
public class MandateDao extends BaseDao {

    private static final String MANDATE_PREFIX = "MANDATE-";
    private static final String MANDATE_TRIGGERHELPER_PREFIX = "MANDATETRIGGERHELPER-";
    //public static final String DELEGATOR_PREFIX = "USERDELEGATOR-";

    private static final String GSI_INDEX_DELEGATE_STATE = "delegate-state-gsi";

    DynamoDbAsyncTable<MandateEntity> mandateTable;
    DynamoDbAsyncTable<MandateEntity> mandateHistoryTable;
    

    public MandateDao(DynamoDbEnhancedAsyncClient dynamoDbAsyncClient,
                              @Value("${aws.dynamodb.table}") String table,
                              @Value("${aws.dynamodb.table_history}") String tableHistory) {                                      
        this.mandateTable = dynamoDbAsyncClient.table(table, TableSchema.fromBean(MandateEntity.class));            
        this.mandateHistoryTable = dynamoDbAsyncClient.table(tableHistory, TableSchema.fromBean(MandateEntity.class));   
    }

    //#region public methods

    public Flux<MandateEntity> listMandatesByDelegate(String internaluserid, Optional<String> status) {
        // devo sempre filtrare. Se lo stato è passato, vuol dire che voglio filtrare solo per quello stato.
        // altrimenti, è IMPLICITO il fatto di filtrare per le deleghe pendenti e attive (ovvero < 20)
        // NB: listMandatesByDelegate e listMandatesByDelegator si assomigliano, ma a livello di query fanno
        // affidamento ad indici diversi e query diverse
        // listMandatesByDelegate si affida all'indice GSI delegate-state, che filtra per utente delegato E stato.
        
        int i_status = StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE);
        if (status.isPresent())
        {
                i_status = StatusEnumMapper.intValfromValueConst(status.get());               
        }        

        // devo sempre mettere un filtro di salvaguardia per quanto riguarda la scadenza della delega.
        // infatti se una delega è scaduta, potrebbe rimanere a sistema per qualche ora/giorno prima di essere svecchiata
        // e quindi va sempre previsto un filtro sulla data di scadenza
        AttributeValue att = AttributeValue.builder()
                .s(DateUtils.formatDate(LocalDate.now()))
                .build();

        Map<String, AttributeValue> expressionValues = new HashMap<>();
                expressionValues.put(":validto", att);
        Expression exp = Expression.builder()                        
        .expression("d_validto > :validto")
                .expressionValues(expressionValues)
                .build();    
         
        
        // il filtro cambia in base al fatto se ho chiesto uno stato specifico (uso =)
        //   o se invece non chiedo lo stato (e quindi mi interessano pendenti e attive, uso <=)
        QueryEnhancedRequest qeRequest = QueryEnhancedRequest
                .builder()
                .queryConditional(
                        status.isPresent()?QueryConditional.keyEqualTo(getKeyBuild(internaluserid, i_status+"")):
                                        QueryConditional.sortLessThanOrEqualTo(getKeyBuild(internaluserid, i_status+"")))
                .filterExpression(exp)                
                .scanIndexForward(true)                
                .build();

        // viene volutamente ignorata la paginazione, che per ora non serve.
        // si suppone infatti che la lista delle deleghe non sia troppo lunga e quindi non vada a sforare il limite di 1MB di paginazione
        return Flux.from(mandateTable.index(GSI_INDEX_DELEGATE_STATE).query(qeRequest)
                .flatMapIterable(m -> {
                        return m.items();
                }))
                .flatMap(item -> Mono.just(normalizeAfterReadFromDb(item)));   
    }

    public Flux<MandateEntity> listMandatesByDelegator(String internaluserid, Optional<String> status) {
        // devo sempre filtrare. Se lo stato è passato, vuol dire che voglio filtrare solo per quello stato.
        // altrimenti, è IMPLICITO il fatto di filtrare per le deleghe pendenti e attive (ovvero < 20)
        // NB: listMandatesByDelegate e listMandatesByDelegator si assomigliano, ma a livello di query fanno
        // affidamento ad indici diversi e query diverse
        // listMandatesByDelegator si affida all'ordinamento principale, che filtra per utente e delega. Lo stato va previsto a parte nell'expressionfilter
        
        int i_status = StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE);
        if (status.isPresent())
        {
                i_status = StatusEnumMapper.intValfromValueConst(status.get());               
        }        

        // devo sempre mettere un filtro di salvaguardia per quanto riguarda la scadenza della delega.
        // infatti se una delega è scaduta, potrebbe rimanere a sistema per qualche ora/giorno prima di essere svecchiata
        // e quindi va sempre previsto un filtro sulla data di scadenza
        AttributeValue att = AttributeValue.builder()
                .s(DateUtils.formatDate(LocalDate.now()))
                .build();
        AttributeValue attstat = AttributeValue.builder()
                .n(i_status + "")
                .build();

        Map<String, AttributeValue> expressionValues = new HashMap<>();
                expressionValues.put(":validto", att);
                expressionValues.put(":status", attstat);

        Expression exp = Expression.builder()                
        .expression(status.isPresent()?"d_validto > :validto && i_status = :status":
                "d_validto > :validto && i_status <= :status")
                .expressionValues(expressionValues)
                .build();    
       

        QueryEnhancedRequest qeRequest = QueryEnhancedRequest
                .builder()
                .queryConditional(QueryConditional.sortBeginsWith(getKeyBuild(internaluserid, MANDATE_PREFIX)))
                .filterExpression(exp)      
                .scanIndexForward(true)
                .build();

        return Flux.from(mandateTable.query(qeRequest).items())
                .flatMap(item -> Mono.just(normalizeAfterReadFromDb(item)));
    }

    public Mono<Object> acceptMandate(String internaluserid, String mandateId, String verificationCode)
    {
        // Il metodo deve:
        // - leggere l'item dal GSI delegato
        // - validare la richiesta
        // - aggiornarne il contenuto (stato e data accettazione) nell'entity
        // - creare un NUOVO record fantasma, con TTL pari a scadenza della delega (se è prevista). Questo record, quando scadrà, darà luogo ad un loopback 
        //   che mi permetterà di spostare il record principale nello storico. Il TTL NON viene messo nel record principale perchè se qualcosa va storto almeno
        //   il record principale rimane (scaduto) e non viene perso.
        // - update dell'entity aggiornata in DB

        return retrieveMandateForDelegate(internaluserid, mandateId)
                .flatMap(mandate -> {   
                        if (mandate == null)
                                throw new MandateNotFoundException();
        
                        if (!mandate.getValidationcode().equals(verificationCode))
                                throw new InvalidVerificationCodeException();
                        
                        if (mandate.getState() == StatusEnumMapper.intValfromStatus(StatusEnum.PENDING))
                        {
                                // aggiorno lo stato, solo se era in pending. NB: non do errore
                                mandate.setAccepted(DateUtils.formatTime(LocalDateTime.now()));
                                mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE));
                        }
                        
                        return Mono.just(mandate);                        
                })
                .zipWhen(mandate_accepted -> {
                        // Se la delega prevede una scadenza impostata dall'utente, creo un record di supporto con TTL
                        // e quando questo verrà cancellato, dynamoDB invocherà la nostra logica che andrà a spostare il record principale nello storico.
                        // Questo perchè se mettevo il TTL nel record principale e per qualche anomalia non veniva gestito l'evento di cancellazione
                        // avrei perso definitivamente il record della delega (scaduta, ma che va mantenuta per 10 anni nello storico)
                        if (mandate_accepted.getValidto() != null && !mandate_accepted.getValidto().equals(""))
                        {
                                MandateEntity support = new MandateEntity();
                                support.setDelegator(mandate_accepted.getDelegator());
                                support.setId(MANDATE_TRIGGERHELPER_PREFIX + mandate_accepted.getId().replace(MANDATE_PREFIX, ""));
                                long ttlexpiretimestamp = DateUtils.parseDate(mandate_accepted.getValidto()).toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC);
                                support.setTtl(ttlexpiretimestamp);
                                return Mono.fromFuture(mandateTable.putItem(support));
                        }
                        return Mono.empty();                        
                }
                , (mandate_accepted, res) -> {
                        // elimino il record principale
                        UpdateItemEnhancedRequest<MandateEntity> updRequest = UpdateItemEnhancedRequest.builder(MandateEntity.class)
                        .item(mandate_accepted)
                        .ignoreNulls(true)
                        .build();

                        return Mono.fromFuture(mandateTable.updateItem(updRequest));
                });                       

    }

    public Mono<Object> rejectMandate(String internaluserid, String mandateId)
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
        return retrieveMandateForDelegate(internaluserid, mandateId)
        .flatMap(mandate -> {   
                if (mandate == null)
                        throw new MandateNotFoundException();

                if (mandate.getState() == StatusEnumMapper.intValfromStatus(StatusEnum.PENDING)
                        || mandate.getState() == StatusEnumMapper.intValfromStatus(StatusEnum.REJECTED))
                {
                        // aggiorno lo stato, solo se era in pending. NB: non do errore
                        mandate.setRejected(DateUtils.formatTime(LocalDateTime.now()));
                        mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.REJECTED));
                }
                
                return Mono.just(mandate);                        
        })
        .map(mandate_rejected -> {
                //salvo nello storico
                mandate_rejected.setTtl(LocalDateTime.now().plusYears(10).atZone(ZoneId.systemDefault()).toEpochSecond());
                return Mono.fromFuture(mandateHistoryTable.putItem(mandate_rejected));
        })
        .map(mandate_rejected -> {
                // elimino il record principale
                DeleteItemEnhancedRequest delRequest = DeleteItemEnhancedRequest.builder()
                        .key(getKeyBuild(internaluserid, MANDATE_PREFIX + mandateId))
                        .build();

                return Mono.fromFuture(mandateTable.deleteItem(delRequest));
        })
        .map(mandate_rejected -> {
                // elimino l'eventuale record di supporto (se non c'è non mi interessa)
                DeleteItemEnhancedRequest delRequest = DeleteItemEnhancedRequest.builder()
                        .key(getKeyBuild(internaluserid, MANDATE_TRIGGERHELPER_PREFIX + mandateId))                                
                        .build();

                return Mono.fromFuture(mandateTable.deleteItem(delRequest));
        });                          

    } 

    public Mono<Object> revokeMandate(String internaluserid, String mandateId)
    {
        // Il metodo deve:
        // - leggere l'item dalla tabella principale
        // - aggiornarne il contenuto (stato e data revoca) nell'entity
        // - creare una copia dell'entity nella tabella dello storico, impostandone il TTL a 10 anni
        // - eliminare l'entity dalla tabella principale
        // - eliminare eventuale entity di supporto dalla tabella principale

        return retrieveMandateForDelegator(internaluserid, mandateId)
                .map(mandate -> {
                        // aggiorno lo stato
                        mandate.setRevoked(DateUtils.formatTime(LocalDateTime.now()));
                        mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.REVOKED));
                        return mandate;
                })
                .map(mandate_rovoked -> {
                        //salvo nello storico
                        mandate_rovoked.setTtl(LocalDateTime.now().plusYears(10).atZone(ZoneId.systemDefault()).toEpochSecond());
                        return Mono.fromFuture(mandateHistoryTable.putItem(mandate_rovoked));
                })
                .map(mandate_revoked -> {
                        // elimino il record principale
                        DeleteItemEnhancedRequest delRequest = DeleteItemEnhancedRequest.builder()
                                .key(getKeyBuild(internaluserid, MANDATE_PREFIX + mandateId))
                                .build();

                        return Mono.fromFuture(mandateTable.deleteItem(delRequest));
                })
                .map(mandate_deleted -> {
                        // elimino l'eventuale record di supporto (se non c'è non mi interessa)
                        DeleteItemEnhancedRequest delRequest = DeleteItemEnhancedRequest.builder()
                                .key(getKeyBuild(internaluserid, MANDATE_TRIGGERHELPER_PREFIX + mandateId))                                
                                .build();

                        return Mono.fromFuture(mandateTable.deleteItem(delRequest));
                });                

         
    } 
 
    public Mono<MandateEntity> createMandate(MandateEntity mandate){
        PutItemEnhancedRequest<MandateEntity> putRequest = PutItemEnhancedRequest.builder(MandateEntity.class)
                .item(normalizeBeforeWriteInDb(mandate))
                .build();

        return Mono.fromFuture(mandateTable.putItem(putRequest))
                .then(Mono.just(normalizeAfterReadFromDb(mandate)));
    }

    //#endregion

    //#region private methods

    private Mono<MandateEntity> retrieveMandateForDelegator(String internaluserid, String mandateId) {
        // qui l'internaluserid è quello del DELEGANTE, e quindi posso usare direttamente l'informazione per accedere al record
        GetItemEnhancedRequest getitemRequest = GetItemEnhancedRequest.builder()
        .key(getKeyBuild(internaluserid, MANDATE_PREFIX + mandateId))
        .build();


        return Mono.fromFuture(mandateTable.getItem(getitemRequest));
    }
 
    private Mono<MandateEntity> retrieveMandateForDelegate(String internaluserid, String mandateId) {
        // qui l'internaluserid è quello del DELEGATO, e quindi NON posso usare direttamente l'informazione per accedere al record.
        // devo passare quindi per l'indice sul delegato, recuperarmi la riga, aggiornare il contenuto e salvarla.
        // NB: si noti che ci può essere un problema legato alla concorrenza, ma dato che i dati sulle deleghe non cambiano così frequentemente, 
        // si accetta la possibilità dell'improbabilità che arrivino 2 scritture contemporanee nel piccolo lasso di tempo tra query e update...


        // uso l'expression filter per filtrare per mandateid, dato che su questo indice non fa parte della key.
        // si accetta il costo di leggere tutte le righe per un delegato per poi tornarne una
        // non viene filtrato lo stato, dato che questo metodo può essere usato per motivi generici
        AttributeValue attmandateid = AttributeValue.builder()
                .s(MANDATE_PREFIX + mandateId)
                .build();

        Map<String, AttributeValue> expressionValues = new HashMap<>();
                expressionValues.put(":mandateid", attmandateid);
        Expression exp = Expression.builder()                       
                .expression("sk = :mandateid")
                .expressionValues(expressionValues)
                .build();    
         
        
        // il filtro cambia in base al fatto se ho chiesto uno stato specifico (uso =)
        //   o se invece non chiedo lo stato (e quindi mi interessano pendenti e attive, uso <=)
        QueryEnhancedRequest qeRequest = QueryEnhancedRequest
                .builder()
                .queryConditional(QueryConditional.keyEqualTo(getKeyBuild(internaluserid)))
                .filterExpression(exp)                
                .scanIndexForward(true)                
                .build();

        return Flux.from(mandateTable.index(GSI_INDEX_DELEGATE_STATE).query(qeRequest)
                .flatMapIterable(mlist -> {
                        return mlist.items();                        
                }))
                .take(1, true)
                .next();
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

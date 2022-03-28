package it.pagopa.pn.mandate.middleware.db;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@Repository
public class MandateDao extends BaseDao {

    public static final String MANDATE_PREFIX = "MANDATE-";
    public static final String MANDATE_TRIGGERHELPER_PREFIX = "MANDATETRIGGERHELPER-";
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
                }));   
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

        return Flux.from(mandateTable.query(qeRequest).items());
    }

    public Mono<Object> acceptMandate(String internaluserid, String mandateId, String verificationCode)
    {
        // qui l'internaluserid è quello del DELEGATO, e quindi NON posso usare direttamente l'informazione per accedere al record.
        // devo passare quindi per l'indice sul delegato, recuperarmi la riga, aggiornare il contenuto e salvarla.
        // NB: si noti che ci può essere un problema legato alla concorrenza, ma dato che i dati sulle deleghe non cambiano così frequentemente, 
        // si accetta la possibilità dell'improbabilità che arrivino 2 scritture contemporanee nel piccolo lasso di tempo tra query e update...

        // Il metodo deve:
        // - leggere l'item dal GSI delegato
        // - aggiornarne il contenuto (stato e data accettazione) nell'entity
        // - creare un NUOVO record fantasma, con TTL pari a scadenza della delega (se è prevista). Questo record, quando scadrà, darà luogo ad un loopback 
        //   che mi permetterà di spostare il record principale nello storico. Il TTL NON viene messo nel record principale perchè se qualcosa va storto almeno
        //   il record principale rimane (scaduto) e non viene perso.
        // - update dell'entity aggiornata in DB
        // devo sempre mettere un filtro di salvaguardia per quanto riguarda la scadenza della delega.
        // infatti se una delega è scaduta, potrebbe rimanere a sistema per qualche ora/giorno prima di essere svecchiata
        // e quindi va sempre previsto un filtro sulla data di scadenza
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

        
  /*      
        GetItemEnhancedRequest getitemRequest = GetItemEnhancedRequest.builder()
                .key(getKeyBuild(internaluserid, MANDATE_PREFIX + mandateId))
                .build();
*/

        return Flux.from(mandateTable.index(GSI_INDEX_DELEGATE_STATE).query(qeRequest)
                .flatMapIterable(mlist -> {
                        return mlist.items();                        
                }))
                .take(1, true)
                .next()
                .flatMap(mandate -> {                        
                        // aggiorno lo stato
                        mandate.setAccepted(DateUtils.formatTime(LocalDateTime.now()));
                        mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.ACTIVE));
                        return mandate;
                })
                .map(mandate_accepted -> {
                        MandateEntity support = new MandateEntity();
                        support.setDelegator(mandate_accepted.get ) 
                        mandate_accepted.setTtl(LocalDateTime.now().plusYears(10).atZone(ZoneId.systemDefault()).toEpochSecond());
                        return Mono.fromCompletionStage(mandateTable.putItem(mandate_accepted));
                });
                        /*
        Mono.fromCompletionStage(mandateTable.getItem(getitemRequest))
                .map(mandate -> {
                        // aggiorno lo stato
                        mandate.setRevoked(DateUtils.formatTime(LocalDateTime.now()));
                        mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.REVOKED));
                        return mandate;
                })
                .map(mandate_rovoked -> {
                        //salvo nello storico
                        mandate_rovoked.setTtl(LocalDateTime.now().plusYears(10).atZone(ZoneId.systemDefault()).toEpochSecond());
                        return Mono.fromCompletionStage(mandateHistoryTable.putItem(mandate_rovoked));
                })
                .map(mandate_revoked -> {
                        // elimino il record principale
                        DeleteItemEnhancedRequest delRequest = DeleteItemEnhancedRequest.builder()
                                .key(getKeyBuild(internaluserid, MANDATE_PREFIX + mandateId))
                                .build();

                        return Mono.fromCompletionStage(mandateTable.deleteItem(delRequest));
                })
                .map(mandate_deleted -> {
                        // elimino l'eventuale record di supporto (se non c'è non mi interessa)
                        DeleteItemEnhancedRequest delRequest = DeleteItemEnhancedRequest.builder()
                                .key(getKeyBuild(internaluserid, MANDATE_TRIGGERHELPER_PREFIX + mandateId))                                
                                .build();

                        return Mono.fromCompletionStage(mandateTable.deleteItem(delRequest));
                });     */           

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
        return Mono.empty();
    } 

    public Mono<Object> revokeMandate(String internaluserid, String mandateId)
    {
        // qui l'internaluserid è quello del DELEGANTE, e quindi posso usare direttamente l'informazione per accedere al record

        // Il metodo deve:
        // - leggere l'item dalla tabella principale
        // - aggiornarne il contenuto (stato e data revoca) nell'entity
        // - creare una copia dell'entity nella tabella dello storico, impostandone il TTL a 10 anni
        // - eliminare l'entity dalla tabella principale
        // - eliminare eventuale entity di supporto dalla tabella principale

        GetItemEnhancedRequest getitemRequest = GetItemEnhancedRequest.builder()
                .key(getKeyBuild(internaluserid, MANDATE_PREFIX + mandateId))
                .build();


        return Mono.fromCompletionStage(mandateTable.getItem(getitemRequest))
                .map(mandate -> {
                        // aggiorno lo stato
                        mandate.setRevoked(DateUtils.formatTime(LocalDateTime.now()));
                        mandate.setState(StatusEnumMapper.intValfromStatus(StatusEnum.REVOKED));
                        return mandate;
                })
                .map(mandate_rovoked -> {
                        //salvo nello storico
                        mandate_rovoked.setTtl(LocalDateTime.now().plusYears(10).atZone(ZoneId.systemDefault()).toEpochSecond());
                        return Mono.fromCompletionStage(mandateHistoryTable.putItem(mandate_rovoked));
                })
                .map(mandate_revoked -> {
                        // elimino il record principale
                        DeleteItemEnhancedRequest delRequest = DeleteItemEnhancedRequest.builder()
                                .key(getKeyBuild(internaluserid, MANDATE_PREFIX + mandateId))
                                .build();

                        return Mono.fromCompletionStage(mandateTable.deleteItem(delRequest));
                })
                .map(mandate_deleted -> {
                        // elimino l'eventuale record di supporto (se non c'è non mi interessa)
                        DeleteItemEnhancedRequest delRequest = DeleteItemEnhancedRequest.builder()
                                .key(getKeyBuild(internaluserid, MANDATE_TRIGGERHELPER_PREFIX + mandateId))                                
                                .build();

                        return Mono.fromCompletionStage(mandateTable.deleteItem(delRequest));
                });                

         
    } 
 
 
    private Mono<Object> updateMandate(MandateEntity item) {
       
       

        UpdateItemEnhancedRequest<MandateEntity> requestConsumer = UpdateItemEnhancedRequest.builder(MandateEntity.class)
                .item(item)
                .ignoreNulls(true)      //importante altrimenti "sovrascrive" tutto l'oggetto a null
                .build();

        return Mono.fromCompletionStage(mandateTable.updateItem(requestConsumer));
    }

}

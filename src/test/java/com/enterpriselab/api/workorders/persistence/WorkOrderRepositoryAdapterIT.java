package com.enterpriselab.api.workorders.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.enterpriselab.api.AbstractPostgresIT;
import com.enterpriselab.api.shared.domain.NotFoundException;
import com.enterpriselab.api.shared.domain.PageQuery;
import com.enterpriselab.api.shared.domain.PageResult;
import com.enterpriselab.api.workorders.domain.ClosingNote;
import com.enterpriselab.api.workorders.domain.MachineRef;
import com.enterpriselab.api.workorders.domain.Priority;
import com.enterpriselab.api.workorders.domain.TakenBy;
import com.enterpriselab.api.workorders.domain.WorkOrder;
import com.enterpriselab.api.workorders.domain.WorkOrderFilter;
import com.enterpriselab.api.workorders.domain.WorkOrderRepository;
import com.enterpriselab.api.workorders.domain.WorkOrderStatus;
import com.enterpriselab.api.workorders.domain.WorkOrderType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REQ-2, REQ-4 a REQ-12, REQ-32 a REQ-36 y REQ-39: el adaptador contra Postgres real y el
 * seed de dev. Las órdenes de prueba llevan el título {@code WORA-...} y se limpian al
 * terminar; las 32 del seed solo se leen. Cada búsqueda filtra por el prefijo propio, así
 * que no depende de lo que dejaron las otras IT, que comparten la base.
 */
@ActiveProfiles("dev")
class WorkOrderRepositoryAdapterIT extends AbstractPostgresIT {

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00.123Z");

    @Autowired
    private WorkOrderRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private WorkOrderJpaRepository jpaRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from work_orders where title like 'WORA-%'");
    }

    // --- Alta y lectura ------------------------------------------------------------------------------

    @Test
    void savesANewOrderOnAWholeMachineAndReadsItBack() {
        WorkOrder saved = repository.save(order("WORA-machine", null, "Envasadora línea 1", ""));

        assertThat(saved.id()).isNotNull();
        assertThat(saved).isEqualTo(new WorkOrder(saved.id(), "WORA-machine", "Descripción de prueba",
                new MachineRef(1L, null, "Envasadora línea 1", ""), WorkOrderType.CORRECTIVO, Priority.MEDIUM,
                WorkOrderStatus.PENDING, NOW, null, null));
        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void savesAnOrderOnAPartWithItsCommentAndBreadcrumb() {
        WorkOrder saved = repository.save(order("WORA-part", 3L, "Envasadora línea 1 > Mesa de transporte > Cinta 1 > "
                + "Motor de cinta", "Hace ruido"));

        WorkOrder read = repository.findById(saved.id()).orElseThrow();

        assertThat(read.machineRef()).isEqualTo(new MachineRef(1L, 3L,
                "Envasadora línea 1 > Mesa de transporte > Cinta 1 > Motor de cinta", "Hace ruido"));
    }

    @Test
    void theCreatedAtKeepsMillisecondsAndAnUnknownIdIsEmpty() {
        WorkOrder saved = repository.save(order("WORA-ms", null, "Envasadora línea 1", ""));

        assertThat(saved.createdAt()).isEqualTo(NOW.truncatedTo(ChronoUnit.MILLIS));
        assertThat(repository.findById(-1L)).isEmpty();
    }

    @Test
    void savesAnOrderWithAnOwnerAndAClosingNote() {
        long userId = jdbcTemplate.queryForObject("select id from users where username = 'tecnico'", Long.class);
        Instant at = Instant.parse("2026-09-30T13:00:00Z");
        WorkOrder closed = new WorkOrder(null, "WORA-closed", "Descripción de prueba",
                new MachineRef(1L, null, "Envasadora línea 1", ""), WorkOrderType.PREVENTIVO, Priority.LOW,
                WorkOrderStatus.COMPLETED, NOW, new TakenBy(userId, "Técnico Mecánico de Guardia", at),
                new ClosingNote("Se resolvió y se verificó el funcionamiento correcto.", userId,
                        "Técnico Mecánico de Guardia", at));

        WorkOrder read = repository.findById(repository.save(closed).id()).orElseThrow();

        assertThat(read.takenBy()).isEqualTo(new TakenBy(userId, "Técnico Mecánico de Guardia", at));
        assertThat(read.closingNote()).isEqualTo(new ClosingNote(
                "Se resolvió y se verificó el funcionamiento correcto.", userId, "Técnico Mecánico de Guardia", at));
        assertThat(read.status()).isEqualTo(WorkOrderStatus.COMPLETED);
    }

    @Test
    void theSeededOrder3IsReadWithItsOwnerAndClosingNote() {
        WorkOrder order = repository.findById(3L).orElseThrow();

        assertThat(order.machineRef().breadcrumb()).isEqualTo(
                "Envasadora línea 1 > Mesa de transporte > Cinta 1 > Motor de cinta");
        assertThat(order.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(order.takenBy().name()).isEqualTo("Técnico Electricista Preventivo");
        assertThat(order.takenBy().at()).isEqualTo(Instant.parse("2026-08-03T15:00:00Z"));
        assertThat(order.closingNote().at()).isEqualTo(Instant.parse("2026-08-03T20:00:00Z"));
    }

    // --- Edición y baja -------------------------------------------------------------------------------

    @Test
    void savingAnExistingIdChangesOnlyTitleDescriptionAndPriority() {
        WorkOrder created = repository.save(order("WORA-edit", 3L, "Ruta original", "Comentario"));
        jdbcTemplate.update("update work_orders set status = 'in-progress', taken_by_id = "
                + "(select id from users where username = 'tecnico'), taken_by_name = 'Dueño', taken_at = now() "
                + "where id = ?", created.id());

        // Aunque el llamador mande otro tipo, máquina, parte, estado o dueño, nada de eso cambia por esta vía.
        WorkOrder attempt = new WorkOrder(created.id(), "WORA-edit-2", "Otra descripción larga",
                new MachineRef(2L, 9L, "Otra ruta", "Otro comentario"), WorkOrderType.PRONTO_INTERVENCION,
                Priority.HIGH, WorkOrderStatus.CANCELLED, NOW.plusSeconds(60), null, null);

        WorkOrder updated = repository.save(attempt);

        assertThat(updated.title()).isEqualTo("WORA-edit-2");
        assertThat(updated.description()).isEqualTo("Otra descripción larga");
        assertThat(updated.priority()).isEqualTo(Priority.HIGH);
        assertThat(updated.type()).isEqualTo(WorkOrderType.CORRECTIVO);
        assertThat(updated.machineRef()).isEqualTo(created.machineRef());
        assertThat(updated.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
        assertThat(updated.takenBy()).isNotNull();
        assertThat(updated.takenBy().name()).isEqualTo("Dueño");
    }

    @Test
    void savingAnOrderThatWasDeletedAtTheLastMomentIsNotFound() {
        assertThatThrownBy(() -> repository.save(new WorkOrder(-1L, "WORA-gone", "Descripción de prueba",
                new MachineRef(1L, null, "X", ""), WorkOrderType.CORRECTIVO, Priority.LOW, WorkOrderStatus.PENDING,
                NOW, null, null))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deletingRemovesTheOrderAndAnUnknownIdIsANoOp() {
        WorkOrder created = repository.save(order("WORA-delete", null, "Envasadora línea 1", ""));

        repository.deleteById(created.id());
        repository.deleteById(created.id());

        assertThat(repository.findById(created.id())).isEmpty();
    }

    // --- Listado: paginación (REQ-2, REQ-4, REQ-5) ----------------------------------------------------

    @Test
    void pagesComeInIdOrderWithRealTotals() {
        for (int n = 1; n <= 5; n++) {
            repository.save(order("WORA-page-" + n, null, "Envasadora línea 1", ""));
        }
        WorkOrderFilter filter = filter("wora-page-", null, null);

        PageResult<WorkOrder> first = repository.search(filter, new PageQuery(1, 2));
        PageResult<WorkOrder> third = repository.search(filter, new PageQuery(3, 2));

        assertThat(first.items()).extracting(WorkOrder::title).containsExactly("WORA-page-1", "WORA-page-2");
        assertThat(third.items()).extracting(WorkOrder::title).containsExactly("WORA-page-5");
        assertThat(first.totalItems()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.page()).isEqualTo(1);
        assertThat(first.size()).isEqualTo(2);
    }

    @Test
    void aPageBeyondTheLastIsEmptyWithRealTotals() {
        for (int n = 1; n <= 3; n++) {
            repository.save(order("WORA-far-" + n, null, "Envasadora línea 1", ""));
        }

        PageResult<WorkOrder> far = repository.search(filter("WORA-far-", null, null), new PageQuery(99, 2));

        assertThat(far.items()).isEmpty();
        assertThat(far.totalItems()).isEqualTo(3);
        assertThat(far.totalPages()).isEqualTo(2);
    }

    @Test
    void noMatchesGiveAnEmptyPageWithZeroTotals() {
        PageResult<WorkOrder> none = repository.search(filter("WORA-no-such-title-zzz", null, null),
                new PageQuery(1, 10));

        assertThat(none.items()).isEmpty();
        assertThat(none.totalItems()).isZero();
        assertThat(none.totalPages()).isZero();
    }

    @Test
    void theSeededOrdersComeFirstAndOrderedByNumericId() {
        PageResult<WorkOrder> page = repository.search(filter(null, null, null), new PageQuery(1, 10));

        assertThat(page.items()).extracting(WorkOrder::id).startsWith(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(page.totalItems()).isGreaterThanOrEqualTo(32);
        // 9 < 10 < 11: el orden es numérico y no de texto.
        assertThat(repository.search(filter(null, null, null), new PageQuery(2, 10)).items())
                .extracting(WorkOrder::id).startsWith(11L, 12L);
    }

    // --- Listado: búsqueda por título (REQ-6, REQ-7, REQ-8) -------------------------------------------

    @Test
    void theTitleSearchIgnoresCaseAndMatchesAnywhere() {
        repository.save(order("WORA-Motor Principal", null, "M", ""));
        repository.save(order("WORA-otro", null, "M", ""));

        assertThat(titles(filter("wora-motor principal", null, null))).containsExactly("WORA-Motor Principal");
        assertThat(titles(filter("WORA-MOTOR", null, null))).containsExactly("WORA-Motor Principal");
        // Sin el prefijo propio también encuentra la orden sembrada "Revisar motor principal".
        assertThat(titles(filter("motor principal", null, null)))
                .contains("WORA-Motor Principal", "Revisar motor principal");
        assertThat(titles(filter("ra-motor", null, null))).containsExactly("WORA-Motor Principal");
    }

    @Test
    void percentAndUnderscoreAreLiteralCharacters() {
        repository.save(order("WORA-100% listo", null, "M", ""));
        repository.save(order("WORA-1000 listo", null, "M", ""));
        repository.save(order("WORA-a_b", null, "M", ""));
        repository.save(order("WORA-axb", null, "M", ""));

        assertThat(titles(filter("100%", null, null))).containsExactly("WORA-100% listo");
        assertThat(titles(filter("wora-a_b", null, null))).containsExactly("WORA-a_b");
        assertThat(titles(filter("%", null, null))).contains("WORA-100% listo")
                .doesNotContain("WORA-1000 listo", "WORA-a_b", "WORA-axb");
        assertThat(titles(filter("_", null, null))).contains("WORA-a_b").doesNotContain("WORA-axb");
    }

    @Test
    void theBackslashIsALiteralCharacterToo() {
        repository.save(order("WORA-ruta c:\\temp", null, "M", ""));
        repository.save(order("WORA-ruta c:temp", null, "M", ""));

        assertThat(titles(filter("c:\\temp", null, null))).containsExactly("WORA-ruta c:\\temp");
    }

    @Test
    void aNullTitleTextDoesNotFilterByTitle() {
        repository.save(order("WORA-any", null, "M", ""));

        PageResult<WorkOrder> all = repository.search(filter(null, null, null), new PageQuery(1, 100));

        assertThat(all.totalItems()).isGreaterThan(32);
    }

    // --- Listado: estado y prioridad (REQ-9, REQ-10, REQ-11) ------------------------------------------

    @Test
    void theStatusAndPriorityFiltersAndTheirCombination() {
        WorkOrder a = repository.save(order("WORA-f-a", null, "M", "", Priority.LOW));
        WorkOrder b = repository.save(order("WORA-f-b", null, "M", "", Priority.HIGH));
        WorkOrder c = repository.save(order("WORA-f-c", null, "M", "", Priority.HIGH));
        // V8 (spec 04): una orden cerrada tiene dueño y nota, con el mismo autor.
        String closeAs = "update work_orders set status = ?, taken_by_id = u.id, taken_by_name = 'Dueño', "
                + "taken_at = now(), closing_comment = 'Nota', closing_author_id = u.id, "
                + "closing_author_name = 'Dueño', closed_at = now() from users u "
                + "where u.username = 'tecnico' and work_orders.id in (%s)";
        jdbcTemplate.update(closeAs.formatted("?, ?"), "completed", b.id(), a.id());
        jdbcTemplate.update(closeAs.formatted("?"), "cancelled", c.id());

        assertThat(titles(filter("wora-f-", WorkOrderStatus.COMPLETED, null))).containsExactly("WORA-f-a", "WORA-f-b");
        assertThat(titles(filter("wora-f-", null, Priority.HIGH))).containsExactly("WORA-f-b", "WORA-f-c");
        assertThat(titles(filter("wora-f-", WorkOrderStatus.COMPLETED, Priority.HIGH))).containsExactly("WORA-f-b");
        assertThat(titles(filter("wora-f-", WorkOrderStatus.PENDING, Priority.HIGH))).isEmpty();
        PageResult<WorkOrder> completed = repository.search(filter("wora-f-", WorkOrderStatus.COMPLETED, null),
                new PageQuery(1, 1));
        assertThat(completed.items()).hasSize(1);
        assertThat(completed.totalItems()).isEqualTo(2);
        assertThat(completed.totalPages()).isEqualTo(2);
    }

    @Test
    void theStatusFilterOverTheSeedCountsItsOrders() {
        assertThat(repository.search(filter(null, WorkOrderStatus.CANCELLED, null), new PageQuery(1, 100))
                .totalItems()).isGreaterThanOrEqualTo(2);
        assertThat(repository.search(filter(null, WorkOrderStatus.IN_PROGRESS, null), new PageQuery(1, 100))
                .items()).allSatisfy(o -> assertThat(o.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS));
    }

    // --- Transiciones (spec 04: REQ-7, REQ-20, REQ-21, REQ-22, REQ-28, REQ-29) ---------------------------

    @Test
    void takeWritesTheOwnerAndTheRereadSeesIt() {
        WorkOrder saved = repository.save(order("WORA-take", null, "M", ""));
        long userId = userId("tecnico");

        WorkOrder taken = repository.take(saved.id(), new TakenBy(userId, "Técnico", NOW)).orElseThrow();

        assertThat(taken.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(taken.takenBy()).isEqualTo(new TakenBy(userId, "Técnico", NOW));
        assertThat(taken.closingNote()).isNull();
        assertThat(repository.findById(saved.id())).contains(taken);
    }

    @Test
    void takeOnAnOrderThatIsNotPendingTouchesNothing() {
        WorkOrder saved = repository.save(order("WORA-take2", null, "M", ""));
        long userId = userId("tecnico");
        repository.take(saved.id(), new TakenBy(userId, "Técnico", NOW)).orElseThrow();

        assertThat(repository.take(saved.id(), new TakenBy(userId("electricista"), "Otro", NOW))).isEmpty();
        assertThat(repository.findById(saved.id()).orElseThrow().takenBy().userId()).isEqualTo(userId);
    }

    @Test
    void closeWritesTheNoteAndKeepsTheOwner() {
        WorkOrder saved = repository.save(order("WORA-close", null, "M", ""));
        long userId = userId("tecnico");
        repository.take(saved.id(), new TakenBy(userId, "Técnico", NOW)).orElseThrow();

        WorkOrder closed = repository.close(saved.id(), userId, WorkOrderStatus.CANCELLED,
                new ClosingNote("Nota de cierre", userId, "Técnico", NOW.plusSeconds(5))).orElseThrow();

        assertThat(closed.status()).isEqualTo(WorkOrderStatus.CANCELLED);
        assertThat(closed.takenBy()).isEqualTo(new TakenBy(userId, "Técnico", NOW));
        assertThat(closed.closingNote()).isEqualTo(
                new ClosingNote("Nota de cierre", userId, "Técnico", NOW.plusSeconds(5)));
        assertThat(repository.findById(saved.id())).contains(closed);
    }

    @Test
    void closeTouchesNothingForAWrongStatusAnotherOwnerOrAMissingId() {
        WorkOrder pending = repository.save(order("WORA-close-p", null, "M", ""));
        WorkOrder mine = repository.save(order("WORA-close-m", null, "M", ""));
        long userId = userId("tecnico");
        long otherId = userId("electricista");
        repository.take(mine.id(), new TakenBy(userId, "Técnico", NOW)).orElseThrow();
        ClosingNote note = new ClosingNote("Nota", otherId, "Otro", NOW);

        assertThat(repository.close(pending.id(), userId, WorkOrderStatus.COMPLETED, note)).isEmpty();
        assertThat(repository.close(mine.id(), otherId, WorkOrderStatus.COMPLETED, note)).isEmpty();
        assertThat(repository.close(-1L, userId, WorkOrderStatus.COMPLETED, note)).isEmpty();
        assertThat(repository.findById(mine.id()).orElseThrow().status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(repository.findById(pending.id()).orElseThrow().status()).isEqualTo(WorkOrderStatus.PENDING);
    }

    @Test
    void releaseClearsTheOwnerAndAnythingElseTouchesNothing() {
        WorkOrder saved = repository.save(order("WORA-release", null, "M", ""));
        long userId = userId("tecnico");
        assertThat(repository.release(saved.id())).isEmpty();
        repository.take(saved.id(), new TakenBy(userId, "Técnico", NOW)).orElseThrow();

        WorkOrder released = repository.release(saved.id()).orElseThrow();

        assertThat(released.status()).isEqualTo(WorkOrderStatus.PENDING);
        assertThat(released.takenBy()).isNull();
        assertThat(released.closingNote()).isNull();
        assertThat(repository.release(-1L)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select taken_at from work_orders where id = ?", Object.class,
                saved.id())).isNull();
    }

    /** REQ-28: una entity leída antes de un cierre y guardada después no pisa estado, dueño ni nota. */
    @Test
    void aStaleEntitySavedAfterACloseDoesNotOverwriteTheTransition() {
        WorkOrder saved = repository.save(order("WORA-stale", null, "M", ""));
        long userId = userId("tecnico");
        repository.take(saved.id(), new TakenBy(userId, "Técnico", NOW)).orElseThrow();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate inner = new TransactionTemplate(transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(status -> {
            WorkOrderEntity stale = jpaRepository.findById(saved.id()).orElseThrow();
            inner.executeWithoutResult(other -> repository.close(saved.id(), userId, WorkOrderStatus.COMPLETED,
                    new ClosingNote("Nota", userId, "Técnico", NOW)).orElseThrow());
            stale.updateDetails("WORA-stale-edited", "Descripción editada", "high");
            jpaRepository.saveAndFlush(stale);
        });

        WorkOrder read = repository.findById(saved.id()).orElseThrow();
        assertThat(read.title()).isEqualTo("WORA-stale-edited");
        assertThat(read.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(read.takenBy().userId()).isEqualTo(userId);
        assertThat(read.closingNote().comment()).isEqualTo("Nota");
    }

    private long userId(String username) {
        return jdbcTemplate.queryForObject("select id from users where username = ?", Long.class, username);
    }

    // --- Ayudas ---------------------------------------------------------------------------------------

    private List<String> titles(WorkOrderFilter filter) {
        return repository.search(filter, new PageQuery(1, 100)).items().stream().map(WorkOrder::title).toList();
    }

    private static WorkOrderFilter filter(String title, WorkOrderStatus status, Priority priority) {
        return new WorkOrderFilter(title, status, priority);
    }

    private static WorkOrder order(String title, Long partId, String breadcrumb, String comment) {
        return order(title, partId, breadcrumb, comment, Priority.MEDIUM);
    }

    private static WorkOrder order(String title, Long partId, String breadcrumb, String comment, Priority priority) {
        return new WorkOrder(null, title, "Descripción de prueba", new MachineRef(1L, partId, breadcrumb, comment),
                WorkOrderType.CORRECTIVO, priority, WorkOrderStatus.PENDING, NOW, null, null);
    }
}

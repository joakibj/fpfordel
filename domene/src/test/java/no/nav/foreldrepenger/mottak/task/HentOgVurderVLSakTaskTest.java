package no.nav.foreldrepenger.mottak.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import no.nav.foreldrepenger.fordel.kodeverdi.BehandlingTema;
import no.nav.foreldrepenger.mottak.felles.MottakMeldingDataWrapper;
import no.nav.foreldrepenger.mottak.klient.FagsakTjeneste;
import no.nav.foreldrepenger.mottak.klient.VurderFagsystemResultat;
import no.nav.vedtak.exception.TekniskException;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskData;
import no.nav.vedtak.felles.prosesstask.api.ProsessTaskRepository;

@ExtendWith(MockitoExtension.class)
public class HentOgVurderVLSakTaskTest {

    @Mock
    private FagsakTjeneste fagsakRestKlientMock;
    @Mock
    private ProsessTaskRepository prosessTaskRepository;
    private String saksnummer = "123456";
    private String aktørId = "9000000000009";

    private LocalDate termindato = LocalDate.now();

    @Test
    public void precondition_skal_feile_ved_manglende_aktørId() {
        MottakMeldingDataWrapper dataWrapper = lagDataWrapper();
        dataWrapper.setAktørId(null);
        HentOgVurderVLSakTask task = new HentOgVurderVLSakTask(prosessTaskRepository, fagsakRestKlientMock);
        assertTrue(assertThrows(TekniskException.class, () -> task.precondition(dataWrapper)).getMessage().contains("FP-941984"));
    }

    @Test
    public void postcondition_skal_feile_ved_manglende_aktørId() {
        MottakMeldingDataWrapper dataWrapper = lagDataWrapper();
        dataWrapper.setAktørId(null);

        HentOgVurderVLSakTask task = new HentOgVurderVLSakTask(prosessTaskRepository, fagsakRestKlientMock);
        var e = assertThrows(TekniskException.class, () -> task.postcondition(dataWrapper));
        assertTrue(e.getMessage().contains("FP-638068"));
    }

    @Test
    public void neste_steg_skal_være_til_journalføring_når_vurderFagsystem_returnerer_VL_og_saksnummer() {
        when(fagsakRestKlientMock.vurderFagsystem(any())).thenReturn(lagFagsystemSvar(saksnummer, null, true, false, false, false));

        HentOgVurderVLSakTask task = new HentOgVurderVLSakTask(prosessTaskRepository, fagsakRestKlientMock);
        MottakMeldingDataWrapper dataWrapper = lagDataWrapper();

        MottakMeldingDataWrapper result = task.doTask(dataWrapper);

        assertThat(result.getProsessTaskData().getTaskType()).isEqualTo(TilJournalføringTask.TASKNAME);
    }

    @Test
    public void neste_steg_skal_være_til_journalføring_når_vurderFagsystem_returnerer_VL_uten_saksnummer() {
        when(fagsakRestKlientMock.vurderFagsystem(any())).thenReturn(lagFagsystemSvar(null, null, true, false, false, false));

        HentOgVurderVLSakTask task = new HentOgVurderVLSakTask(prosessTaskRepository, fagsakRestKlientMock);
        MottakMeldingDataWrapper dataWrapper = lagDataWrapper();
        MottakMeldingDataWrapper result = task.doTask(dataWrapper);

        assertThat(result.getProsessTaskData().getTaskType()).isEqualTo(OpprettSakTask.TASKNAME);
    }

    @Test
    public void neste_steg_skal_være_hentOgVurderInfotrygd_når_vurderFagsystem_returnerer_sjekkInfotrygd() {
        when(fagsakRestKlientMock.vurderFagsystem(any())).thenReturn(lagFagsystemSvar(null, null, false, true, false, false));

        HentOgVurderVLSakTask task = new HentOgVurderVLSakTask(prosessTaskRepository, fagsakRestKlientMock);
        MottakMeldingDataWrapper dataWrapper = lagDataWrapper();
        MottakMeldingDataWrapper result = task.doTask(dataWrapper);

        assertThat(result.getProsessTaskData().getTaskType()).isEqualTo(HentOgVurderInfotrygdSakTask.TASKNAME);
    }

    @Test
    public void neste_steg_skal_være_opprettGsakOppgave_når_vurderFagsystem_returnerer_manuell_vurdering() {
        when(fagsakRestKlientMock.vurderFagsystem(any())).thenReturn(lagFagsystemSvar(null, null, false, false, true, false));

        HentOgVurderVLSakTask task = new HentOgVurderVLSakTask(prosessTaskRepository, fagsakRestKlientMock);
        MottakMeldingDataWrapper dataWrapper = lagDataWrapper();
        MottakMeldingDataWrapper result = task.doTask(dataWrapper);

        assertThat(result.getProsessTaskData().getTaskType()).isEqualTo(MidlJournalføringTask.TASKNAME);
    }

    @Test
    public void neste_steg_skal_være_opprettGsakOppgave_når_vurderFagsystem_returnerer_VL_uten_saksnummer_og_barnet_er_født_før_19_og_annen_part_har_rett() {
        when(fagsakRestKlientMock.vurderFagsystem(any())).thenReturn(lagFagsystemSvar(null, null, true, false, false, false));

        HentOgVurderVLSakTask task = new HentOgVurderVLSakTask(prosessTaskRepository, fagsakRestKlientMock);
        MottakMeldingDataWrapper dataWrapper = lagDataWrapper();
        dataWrapper.setAnnenPartHarRett(true);
        dataWrapper.setBarnFodselsdato(LocalDate.of(2018, 5, 17));
        MottakMeldingDataWrapper result = task.doTask(dataWrapper);

        assertThat(result.getProsessTaskData().getTaskType()).isEqualTo(MidlJournalføringTask.TASKNAME);
    }

    @Test
    public void neste_steg_skal_være_til_journalføring_når_vurderFagsystem_returnerer_VL_og_saksnummer_selv_om_barnet_er_født_før_19_og_annen_part_har_rett() {
        when(fagsakRestKlientMock.vurderFagsystem(any())).thenReturn(lagFagsystemSvar(saksnummer, null, true, false, false, false));

        HentOgVurderVLSakTask task = new HentOgVurderVLSakTask(prosessTaskRepository, fagsakRestKlientMock);
        MottakMeldingDataWrapper dataWrapper = lagDataWrapper();
        dataWrapper.setAnnenPartHarRett(true);
        dataWrapper.setBarnFodselsdato(LocalDate.of(2018, 5, 17));

        MottakMeldingDataWrapper result = task.doTask(dataWrapper);

        assertThat(result.getProsessTaskData().getTaskType()).isEqualTo(TilJournalføringTask.TASKNAME);
    }

    @Test
    public void neste_steg_skal_være_til_journalføring_når_vurderFagsystem_returnerer_VL_og_saksnummer_når_omsorg_før_19() {
        when(fagsakRestKlientMock.vurderFagsystem(any())).thenReturn(lagFagsystemSvar(saksnummer, null, true, false, false, false));

        HentOgVurderVLSakTask task = new HentOgVurderVLSakTask(prosessTaskRepository, fagsakRestKlientMock);
        MottakMeldingDataWrapper dataWrapper = lagDataWrapper();
        dataWrapper.setOmsorgsovertakelsedato(LocalDate.of(2018, 5, 17));

        MottakMeldingDataWrapper result = task.doTask(dataWrapper);

        assertThat(result.getProsessTaskData().getTaskType()).isEqualTo(TilJournalføringTask.TASKNAME);
    }

    @Test
    public void neste_steg_skal_være_hentOgVurderVLSak_når_vurderFagsystem_returnerer_prøv_igjen() {
        when(fagsakRestKlientMock.vurderFagsystem(any()))
                .thenReturn(lagFagsystemSvar(null, LocalDateTime.now().plusDays(1), false, false, false, true));

        HentOgVurderVLSakTask task = new HentOgVurderVLSakTask(prosessTaskRepository, fagsakRestKlientMock);
        MottakMeldingDataWrapper dataWrapper = lagDataWrapper();
        MottakMeldingDataWrapper result = task.doTask(dataWrapper);

        assertThat(result.getProsessTaskData().getTaskType()).isEqualTo(HentOgVurderVLSakTask.TASKNAME);
    }

    @Test
    public void neste_steg_skal_være_tilJournalføring_når_vl_returnerer_saksnummer_for_svangerskapspenger() {
        when(fagsakRestKlientMock.vurderFagsystem(any())).thenReturn(lagFagsystemSvar(saksnummer, null, true, false, false, false));

        HentOgVurderVLSakTask task = new HentOgVurderVLSakTask(prosessTaskRepository, fagsakRestKlientMock);

        ProsessTaskData data = new ProsessTaskData(HentOgVurderVLSakTask.TASKNAME);
        data.setSekvens("1");
        MottakMeldingDataWrapper dataWrapper = new MottakMeldingDataWrapper(data);
        dataWrapper.setAktørId(aktørId);
        dataWrapper.setBarnTermindato(termindato);
        dataWrapper.setBehandlingTema(BehandlingTema.SVANGERSKAPSPENGER);

        MottakMeldingDataWrapper result = task.doTask(dataWrapper);

        assertThat(result.getProsessTaskData().getTaskType()).isEqualTo(TilJournalføringTask.TASKNAME);
    }

    private MottakMeldingDataWrapper lagDataWrapper() {
        ProsessTaskData data = new ProsessTaskData(HentOgVurderVLSakTask.TASKNAME);
        data.setSekvens("1");
        MottakMeldingDataWrapper dataWrapper = new MottakMeldingDataWrapper(data);
        dataWrapper.setAktørId(aktørId);
        dataWrapper.setBarnTermindato(termindato);
        dataWrapper.setBehandlingTema(BehandlingTema.FORELDREPENGER);
        return dataWrapper;
    }

    private static VurderFagsystemResultat lagFagsystemSvar(String saksnummer, LocalDateTime prøvIgjenTidspunkt, boolean behandlesIVL,
            boolean sjekkIT, boolean gsak, boolean prøvIgjen) {
        VurderFagsystemResultat res = new VurderFagsystemResultat();
        res.setSaksnummer(saksnummer);
        res.setPrøvIgjenTidspunkt(prøvIgjenTidspunkt);
        res.setBehandlesIVedtaksløsningen(behandlesIVL);
        res.setManuellVurdering(gsak);
        res.setSjekkMotInfotrygd(sjekkIT);
        res.setPrøvIgjen(prøvIgjen);
        return res;
    }
}
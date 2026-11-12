package tn.esprit.backend_m5.Services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.backend_m5.Entities.BestConfiguration;
import tn.esprit.backend_m5.Entities.OptimizationStudy;
import tn.esprit.backend_m5.Entities.StudyStatus;
import tn.esprit.backend_m5.Entities.Trial;
import tn.esprit.backend_m5.Repositories.BestConfigurationRepository;
import tn.esprit.backend_m5.Repositories.OptimizationStudyRepository;
import tn.esprit.backend_m5.Repositories.TrialRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OptimizationService {

    private static final int DEFAULT_MAX_TRIALS = 20;

    private final OptimizationStudyRepository studyRepository;
    private final TrialRepository trialRepository;
    private final BestConfigurationRepository bestRepository;

    public OptimizationStudy start(OptimizationStudy study) {
        if (study.getMaxTrials() <= 0) {
            study.setMaxTrials(DEFAULT_MAX_TRIALS);
        }
        study.setStatus(StudyStatus.PENDING);
        return studyRepository.save(study);
    }

    public List<OptimizationStudy> list() {
        return studyRepository.findAllByOrderByCreatedAtDesc();
    }

    public OptimizationStudy get(Long id) {
        return findStudy(id);
    }

    public List<Trial> trials(Long id) {
        findStudy(id);
        return trialRepository.findByStudy_IdOrderByTrialNumberAsc(id);
    }

    @Transactional(readOnly = true)
    public BestConfiguration best(Long id) {
        findStudy(id);
        return bestRepository.findByStudy_Id(id)
                .orElseThrow(() -> new RuntimeException("Etude " + id + " : aucune meilleure configuration pour le moment"));
    }

    @Transactional
    public void delete(Long id) {
        findStudy(id);
        bestRepository.deleteByStudy_Id(id);
        bestRepository.flush();
        studyRepository.deleteById(id);
    }

    private OptimizationStudy findStudy(Long id) {
        return studyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Etude " + id + " introuvable"));
    }
}
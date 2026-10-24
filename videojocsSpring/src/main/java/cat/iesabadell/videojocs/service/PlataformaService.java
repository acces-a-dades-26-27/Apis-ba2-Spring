package cat.iesabadell.videojocs.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cat.iesabadell.videojocs.dto.LlancamentResposta;
import cat.iesabadell.videojocs.dto.PreusPlataforma;
import cat.iesabadell.videojocs.model.Llancament;
import cat.iesabadell.videojocs.model.Plataforma;
import cat.iesabadell.videojocs.repository.LlancamentRepository;
import cat.iesabadell.videojocs.repository.PlataformaRepository;

/*
 * Servei de plataformes. La lògica i la resposta HTTP (codi, capçalera i cos) es fan aquí;
 * el controlador només rep la petició i ens la passa.
 * Plataforma no té relacions pròpies: es pot retornar l'ENTITAT directament com a JSON.
 */
@Service
public class PlataformaService {

	// Spring ens injecta el repositori (no fem mai new)
	@Autowired
	private PlataformaRepository repository;

	@Autowired
	private LlancamentRepository lRepository;

	public ResponseEntity<List<Plataforma>> llistar() {
		List<Plataforma> plataformes = repository.findAll(Sort.by("nom"));
		// Una llista buida és un resultat correcte: 200 + []
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Total plataformes: " + plataformes.size())
				.body(plataformes);
	}

	// ResponseEntity<?>: el cos pot ser una Plataforma (si la trobem) o un String (si no)
	public ResponseEntity<?> buscar(int id) {
		Optional<Plataforma> p = repository.findById(id);
		if (p.isPresent()) {
			return ResponseEntity.ok()
					.header("Header-Customitzat", "Dades plataforma ID: " + id)
					.body(p.get());
		} else {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.header("Header-Customitzat", "ID " + id + " NO TROBAT")
					.body("NO EXISTEIX CAP PLATAFORMA AMB ID " + id);
		}
	}

	// ===== Consultes derivades: Spring escriu la consulta a partir del NOM del mètode del repositori =====

	public ResponseEntity<List<Plataforma>> comencenPer(String inici) {
		List<Plataforma> plataformes = repository.findByNomStartingWithIgnoreCaseOrderByNom(inici);
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Plataformes trobades: " + plataformes.size())
				.body(plataformes);
	}

	public ResponseEntity<List<Plataforma>> contenen(String text) {
		List<Plataforma> plataformes = repository.findByNomContainingIgnoreCaseOrderByNom(text);
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Plataformes trobades: " + plataformes.size())
				.body(plataformes);
	}

	// Sense paràmetre: count() heretat de JpaRepository. Amb paràmetre: la consulta derivada countBy...
	public ResponseEntity<Long> total(String inici) {
		long total;
		if (inici == null) {
			total = repository.count();
		} else {
			total = repository.countByNomStartingWithIgnoreCase(inici);
		}
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Total de plataformes")
				.body(total);
	}

	public ResponseEntity<String> crear(String nom) {
		// Regla de negoci: no hi pot haver dues plataformes amb el mateix nom
		if (repository.existsByNom(nom)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.header("Header-Customitzat", "PLATAFORMA DUPLICADA")
					.body("ERROR: JA EXISTEIX LA PLATAFORMA " + nom);
		}
		Plataforma p = repository.save(new Plataforma(nom));
		return ResponseEntity.status(HttpStatus.CREATED)
				.header("Header-Customitzat", "Plataforma creada amb ID: " + p.getId())
				.body("PLATAFORMA INSERIDA: " + p);
	}

	// ===================== SESSIÓ 2 =====================

	public ResponseEntity<String> esborrar(int id) {
		Optional<Plataforma> p = repository.findById(id);
		if (!p.isPresent()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.header("Header-Customitzat", "ID " + id + " NO TROBAT")
					.body("NO EXISTEIX CAP PLATAFORMA AMB ID " + id);
		}
		// Si té llançaments, la FK no deixaria esborrar-la: ho comprovem abans
		if (lRepository.existsByPlataformaId(id)) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.header("Header-Customitzat", "PLATAFORMA AMB LLANCAMENTS")
					.body("ERROR: " + p.get().getNom() + " TE LLANÇAMENTS I NO ES POT ESBORRAR");
		}
		repository.delete(p.get());
		return ResponseEntity.noContent()
				.header("Header-Customitzat", "Plataforma " + id + " esborrada")
				.build();
	}

	// ===================== SESSIÓ 4: CONSULTES =====================

	// Projecció amb record: una sola consulta per a totes les plataformes
	public ResponseEntity<List<PreusPlataforma>> estadistiques() {
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Estadistiques de preus per plataforma")
				.body(repository.estadistiquesPreus());
	}

	// Consulta derivada: llançaments d'una plataforma per sota d'un preu, del més barat al més car
	@Transactional(readOnly = true)
	public ResponseEntity<List<LlancamentResposta>> barats(String nom, BigDecimal maxim) {
		ArrayList<LlancamentResposta> resultat = new ArrayList<>();
		for (Llancament l : lRepository.findByPlataformaNomAndPreuLessThanOrderByPreuAsc(nom, maxim)) {
			resultat.add(LlancamentResposta.de(l));      // llegeix l.videojoc (LAZY): cal la transacció
		}
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Jocs a " + nom + " per sota de " + maxim)
				.body(resultat);
	}

	// Consulta derivada amb límit: els 3 llançaments més cars
	@Transactional(readOnly = true)
	public ResponseEntity<List<LlancamentResposta>> mesCars() {
		ArrayList<LlancamentResposta> resultat = new ArrayList<>();
		for (Llancament l : lRepository.findTop3ByOrderByPreuDesc()) {
			resultat.add(LlancamentResposta.de(l));
		}
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Els 3 llancaments mes cars")
				.body(resultat);
	}

	/*
	 * Modificació massiva amb @Modifying: UN sol UPDATE per a tots els preus de la plataforma.
	 * @Transactional és OBLIGATORI: sense, TransactionRequiredException.
	 * Compareu-ho amb VideojocService.abaixaPreuPerCategoria (bucle + dirty checking).
	 */
	@Transactional
	public ResponseEntity<String> rebaixes(String nom, int percentatge) {
		Optional<Plataforma> p = repository.findByNom(nom);
		if (!p.isPresent()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.header("Header-Customitzat", "PLATAFORMA NO TROBADA")
					.body("NO EXISTEIX LA PLATAFORMA " + nom);
		}
		// 10% de descompte -> multiplicar per 0.90
		BigDecimal factor = BigDecimal.ONE.subtract(
				BigDecimal.valueOf(percentatge).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
		int modificats = repository.aplicarFactor(p.get().getId(), factor);
		return ResponseEntity.ok()
				.header("Header-Customitzat", "Rebaixes aplicades a " + nom)
				.body("S'HAN ABAIXAT " + modificats + " PREUS UN " + percentatge + "% A " + nom);
	}

}

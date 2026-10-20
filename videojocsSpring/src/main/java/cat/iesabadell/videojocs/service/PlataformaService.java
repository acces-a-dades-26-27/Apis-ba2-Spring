package cat.iesabadell.videojocs.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import cat.iesabadell.videojocs.model.Plataforma;
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

}

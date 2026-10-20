package cat.iesabadell.videojocs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cat.iesabadell.videojocs.model.Plataforma;

// JpaRepository<Entitat, tipus de l'@Id>: tot el CRUD sense escriure cap implementació
public interface PlataformaRepository extends JpaRepository<Plataforma, Integer> {

	// Consultes derivades del nom del mètode (sessió 4). Spring escriu la implementació
	Optional<Plataforma> findByNom(String nom);

	boolean existsByNom(String nom);

	// Plataformes que COMENCEN per un text, sense distingir majúscules, ordenades per nom
	//   SELECT ... WHERE UPPER(nom) LIKE UPPER('text%') ORDER BY nom
	List<Plataforma> findByNomStartingWithIgnoreCaseOrderByNom(String inici);

	// Plataformes que CONTENEN un text
	//   SELECT ... WHERE UPPER(nom) LIKE UPPER('%text%') ORDER BY nom
	List<Plataforma> findByNomContainingIgnoreCaseOrderByNom(String text);

	// Quantes plataformes comencen per un text: no carrega les entitats, fa un SELECT COUNT
	long countByNomStartingWithIgnoreCase(String inici);

}

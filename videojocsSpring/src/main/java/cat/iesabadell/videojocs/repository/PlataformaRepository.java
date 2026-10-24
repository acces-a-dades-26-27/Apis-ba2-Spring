package cat.iesabadell.videojocs.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cat.iesabadell.videojocs.dto.PreusPlataforma;
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

	// ===== Projecció amb record i SELECT new (sessió 4) =====
	// Una sola consulta per a totes les plataformes: nombre de jocs, preu mínim, màxim i mitjà
	@Query("""
			SELECT new cat.iesabadell.videojocs.dto.PreusPlataforma(p.nom, COUNT(l), MIN(l.preu), MAX(l.preu), AVG(l.preu))
			FROM Llancament l JOIN l.plataforma p
			GROUP BY p.nom
			ORDER BY p.nom
			""")
	List<PreusPlataforma> estadistiquesPreus();

	// ===== Modificació massiva (sessió 4) =====
	// Un sol UPDATE. Cal @Modifying i que qui el crida sigui @Transactional
	@Modifying(clearAutomatically = true)
	@Query("UPDATE Llancament l SET l.preu = l.preu * :factor WHERE l.plataforma.id = :idPlataforma")
	int aplicarFactor(@Param("idPlataforma") Integer idPlataforma, @Param("factor") BigDecimal factor);

}

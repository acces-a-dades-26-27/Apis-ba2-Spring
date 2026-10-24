package cat.iesabadell.videojocs.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cat.iesabadell.videojocs.model.Categoria;
import cat.iesabadell.videojocs.model.Llancament;

public interface LlancamentRepository extends JpaRepository<Llancament, Integer> {

	// ===== Derivades (sessió 4) =====

	boolean existsByVideojocIdAndPlataformaId(Integer idVideojoc, Integer idPlataforma);

	boolean existsByPlataformaId(Integer idPlataforma);

	List<Llancament> findByPlataformaNomAndPreuLessThanOrderByPreuAsc(String plataforma, BigDecimal preu);

	List<Llancament> findTop3ByOrderByPreuDesc();

	// ===== JPQL (sessió 3): llançaments dels jocs que tenen ALGUNA de les categories =====
	// DISTINCT: un joc pot ser a dues categories del conjunt i no el volem abaixar dues vegades
	@Query("""
			SELECT DISTINCT l FROM Llancament l
			JOIN l.videojoc v
			JOIN v.categories c
			WHERE c IN :categories
			""")
	List<Llancament> findByCategories(@Param("categories") Collection<Categoria> categories);

}

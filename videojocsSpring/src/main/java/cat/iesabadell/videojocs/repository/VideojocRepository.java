package cat.iesabadell.videojocs.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cat.iesabadell.videojocs.model.Videojoc;

public interface VideojocRepository extends JpaRepository<Videojoc, Integer> {

	// ===== Derivades =====

	Optional<Videojoc> findByNom(String nom);

	boolean existsByNom(String nom);

	// ===== Carregar relacions per evitar N+1 (sessió 4) =====

	// Tots els videojocs amb els llançaments ja carregats: UNA consulta en lloc d'1 + N
	@EntityGraph(attributePaths = { "llancaments" })
	List<Videojoc> findAllByOrderByNomAsc();

	// Un videojoc amb els llançaments carregats
	@EntityGraph(attributePaths = { "llancaments" })
	Optional<Videojoc> findWithLlancamentsById(Integer id);

	// llistarPerCategoria del DAO: JOIN per filtrar, JOIN FETCH per al que es farà servir després
	@Query("""
			SELECT v FROM Videojoc v
			JOIN FETCH v.estudi
			JOIN v.categories c
			WHERE c.nom = :categoria
			ORDER BY v.nom
			""")
	List<Videojoc> llistarPerCategoria(@Param("categoria") String categoria);

}

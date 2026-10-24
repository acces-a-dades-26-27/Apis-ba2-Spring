package cat.iesabadell.videojocs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cat.iesabadell.videojocs.model.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

	Optional<Categoria> findByNom(String nom);

}

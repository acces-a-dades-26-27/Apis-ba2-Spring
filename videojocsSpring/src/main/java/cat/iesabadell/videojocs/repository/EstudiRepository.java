package cat.iesabadell.videojocs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cat.iesabadell.videojocs.model.Estudi;

public interface EstudiRepository extends JpaRepository<Estudi, Integer> {

	// El servei fort busca l'estudi pel nom quan es publica un videojoc
	Optional<Estudi> findByNom(String nom);

}

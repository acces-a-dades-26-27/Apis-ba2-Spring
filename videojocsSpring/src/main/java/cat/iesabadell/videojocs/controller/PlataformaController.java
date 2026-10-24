package cat.iesabadell.videojocs.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cat.iesabadell.videojocs.dto.LlancamentResposta;
import cat.iesabadell.videojocs.dto.PreusPlataforma;
import cat.iesabadell.videojocs.model.Plataforma;
import cat.iesabadell.videojocs.service.PlataformaService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

// Fa que la classe sigui un controlador REST: els mètodes retornen DADES (JSON), no vistes
@RestController
// Prefix de totes les URL d'aquesta classe: localhost:8080/api/plataformes
@RequestMapping(path="/api/plataformes")
public class PlataformaController {

	// Connectem automàticament el servei amb @Autowired
	@Autowired
	private PlataformaService service;

	// GET localhost:8080/api/plataformes
	@GetMapping
	public ResponseEntity<List<Plataforma>> llistar() {
		return service.llistar();
	}

	// GET localhost:8080/api/plataformes/2
	@GetMapping(path="/{id}")
	public ResponseEntity<?> buscar(@PathVariable int id) {
		return service.buscar(id);
	}

	// GET localhost:8080/api/plataformes/comencen?inici=p
	@GetMapping(path="/comencen")
	public ResponseEntity<List<Plataforma>> comencenPer(@RequestParam String inici) {
		return service.comencenPer(inici);
	}

	// GET localhost:8080/api/plataformes/contenen?text=s
	@GetMapping(path="/contenen")
	public ResponseEntity<List<Plataforma>> contenen(@RequestParam String text) {
		return service.contenen(text);
	}

	// GET localhost:8080/api/plataformes/total          -> totes
	// GET localhost:8080/api/plataformes/total?inici=p  -> les que comencen per p
	@GetMapping(path="/total")
	public ResponseEntity<Long> total(@RequestParam(required = false) String inici) {
		return service.total(inici);
	}

	// POST localhost:8080/api/plataformes?nom=Xbox
	@PostMapping
	public ResponseEntity<String> crear(@RequestParam String nom) {
		return service.crear(nom);
	}

	// ===================== SESSIÓ 2 =====================

	// DELETE localhost:8080/api/plataformes/4
	@DeleteMapping(path="/{id}")
	public ResponseEntity<String> esborrar(@PathVariable int id) {
		return service.esborrar(id);
	}

	// ===================== SESSIÓ 4 =====================

	// GET localhost:8080/api/plataformes/estadistiques -> projecció amb record
	@GetMapping(path="/estadistiques")
	public ResponseEntity<List<PreusPlataforma>> estadistiques() {
		return service.estadistiques();
	}

	// GET localhost:8080/api/plataformes/Switch/barats?maxim=40 -> consulta derivada
	@GetMapping(path="/{nom}/barats")
	public ResponseEntity<List<LlancamentResposta>> barats(
			@PathVariable String nom,
			@RequestParam @Positive(message = "El preu màxim ha de ser positiu") BigDecimal maxim) {
		return service.barats(nom, maxim);
	}

	// GET localhost:8080/api/plataformes/mes-cars -> consulta derivada amb Top3
	@GetMapping(path="/mes-cars")
	public ResponseEntity<List<LlancamentResposta>> mesCars() {
		return service.mesCars();
	}

	// POST localhost:8080/api/plataformes/PS5/rebaixes?percentatge=10 -> @Modifying
	@PostMapping(path="/{nom}/rebaixes")
	public ResponseEntity<String> rebaixes(
			@PathVariable String nom,
			@RequestParam @Min(value = 1, message = "El percentatge mínim és 1")
					@Max(value = 90, message = "El percentatge màxim és 90") int percentatge) {
		return service.rebaixes(nom, percentatge);
	}

}

package cat.iesabadell.videojocs.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cat.iesabadell.videojocs.model.Plataforma;
import cat.iesabadell.videojocs.service.PlataformaService;

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

}

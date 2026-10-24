package cat.iesabadell.videojocs.exception;

import java.util.LinkedHashMap;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/*
 * Errors de VALIDACIÓ (sessió 2).
 * Quan @Valid falla, Spring NO arriba a cridar el mètode del controlador (ni, per tant, el servei):
 * llança una excepció. Aquesta classe la captura per a TOTS els controladors i en fa la resposta.
 *
 * La resta d'errors (no existeix, duplicat, regla de negoci) els resol el servei retornant
 * el ResponseEntity amb el codi que toca.
 */
@RestControllerAdvice
public class GestorErrors {

	// @Valid @RequestBody ha fallat -> 400 amb un JSON {camp: missatge}
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<LinkedHashMap<String, String>> validacioCos(MethodArgumentNotValidException e) {
		LinkedHashMap<String, String> errors = new LinkedHashMap<>();
		for (FieldError fe : e.getBindingResult().getFieldErrors()) {
			errors.put(fe.getField(), fe.getDefaultMessage());
		}
		return ResponseEntity.badRequest()
				.header("Header-Customitzat", "DADES NO VALIDES")
				.body(errors);
	}

	// Una validació d'un @PathVariable o @RequestParam ha fallat (per exemple, @Positive) -> 400
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<String> validacioParametres(HandlerMethodValidationException e) {
		String missatge = "";
		for (MessageSourceResolvable error : e.getAllErrors()) {
			missatge += error.getDefaultMessage() + "\n";
		}
		return ResponseEntity.badRequest()
				.header("Header-Customitzat", "PARAMETRES NO VALIDS")
				.body(missatge);
	}

}

package com.fantasy.espn.credential;

import com.fantasy.espn.credential.dto.CredentialStatusResponse;
import com.fantasy.espn.credential.dto.SaveCredentialsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ESPN Credentials",
        description = "A user's stored ESPN cookies (espn_s2 + SWID), needed to read private leagues")
@RestController
@RequestMapping("/api/v1/espn/credentials")
public class EspnCredentialController {

    private final EspnCredentialService credentialService;

    public EspnCredentialController(EspnCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @Operation(summary = "Store (or replace) the user's ESPN cookies")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cookies stored"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid cookie values")
    })
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@RequestParam String appUserId, @Valid @RequestBody SaveCredentialsRequest request) {
        credentialService.save(appUserId, request.espnS2(), request.swid());
    }

    @Operation(summary = "Whether the user has stored ESPN cookies")
    @ApiResponse(responseCode = "200", description = "Status returned")
    @GetMapping
    public CredentialStatusResponse status(@RequestParam String appUserId) {
        return new CredentialStatusResponse(credentialService.hasCredentials(appUserId));
    }

    @Operation(summary = "Delete the user's stored ESPN cookies")
    @ApiResponse(responseCode = "204", description = "Cookies deleted (or none existed)")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam String appUserId) {
        credentialService.delete(appUserId);
    }
}

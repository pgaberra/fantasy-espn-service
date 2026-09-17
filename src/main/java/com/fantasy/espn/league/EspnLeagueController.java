package com.fantasy.espn.league;

import com.fantasy.espn.league.dto.AvailablePlayer;
import com.fantasy.espn.league.dto.LeagueSettingsResponse;
import com.fantasy.espn.league.dto.LeagueTeamsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "ESPN Leagues", description = "A user's ESPN fantasy hockey league settings and teams")
@RestController
@Validated
@RequestMapping("/api/v1/espn/leagues")
public class EspnLeagueController {

    /** Deeper than any league's waiver wire, and one ESPN call either way. */
    private static final int MAX_AVAILABLE = 300;

    private final EspnLeagueService leagueService;

    public EspnLeagueController(EspnLeagueService leagueService) {
        this.leagueService = leagueService;
    }

    @Operation(summary = "Get a league's scoring + roster settings")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Settings returned"),
            @ApiResponse(responseCode = "400", description = "League is private (cookies missing/invalid) "
                    + "or the league id/season is malformed"),
            @ApiResponse(responseCode = "404", description = "No such ESPN league for the id and season")
    })
    @GetMapping("/{leagueId}/settings")
    public LeagueSettingsResponse settings(@PathVariable String leagueId,
                                           @RequestParam String appUserId,
                                           @RequestParam(required = false) Integer season) {
        return leagueService.settings(appUserId, season, leagueId);
    }

    @Operation(summary = "List a league's teams (names + which is the user's own)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Teams returned"),
            @ApiResponse(responseCode = "400", description = "League is private (cookies missing/invalid) "
                    + "or the league id/season is malformed"),
            @ApiResponse(responseCode = "404", description = "No such ESPN league for the id and season")
    })
    @GetMapping("/{leagueId}/teams")
    public LeagueTeamsResponse teams(@PathVariable String leagueId,
                                     @RequestParam String appUserId,
                                     @RequestParam(required = false) Integer season) {
        return leagueService.teams(appUserId, season, leagueId);
    }

    @Operation(summary = "List the players the league has available",
            description = "Free agents and players on waivers, most owned across ESPN first. Needs "
                    + "the user's stored cookies, like any private league read.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Available players returned"),
            @ApiResponse(responseCode = "400", description = "League is private (cookies missing/invalid), "
                    + "the league id/season is malformed, or the limit is outside 1-" + MAX_AVAILABLE),
            @ApiResponse(responseCode = "404", description = "No such ESPN league for the id and season")
    })
    @GetMapping("/{leagueId}/free-agents")
    public List<AvailablePlayer> freeAgents(
            @PathVariable @NotBlank @Size(max = 20) String leagueId,
            @RequestParam @NotBlank @Size(max = 128) String appUserId,
            @RequestParam(required = false) Integer season,
            @RequestParam(defaultValue = "150") @Min(1) @Max(MAX_AVAILABLE) int limit) {
        return leagueService.freeAgents(appUserId, season, leagueId, limit);
    }
}

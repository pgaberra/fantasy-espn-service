package com.fantasy.espn.league;

import com.fantasy.espn.league.dto.LeagueSettingsResponse;
import com.fantasy.espn.league.dto.LeagueTeamsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ESPN Leagues", description = "A user's ESPN fantasy hockey league settings and teams")
@RestController
@RequestMapping("/api/v1/espn/leagues")
public class EspnLeagueController {

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
                                           @RequestParam int season) {
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
                                     @RequestParam int season) {
        return leagueService.teams(appUserId, season, leagueId);
    }
}

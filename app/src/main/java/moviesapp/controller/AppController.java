package moviesapp.controller;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.Collections;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.input.MouseButton;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import moviesapp.Movie;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class AppController implements Initializable {

    @FXML
    private TextField nameField;
    @FXML
    private TextField fromYearField;
    @FXML
    private TextField toYearField;
    @FXML
    private ComboBox<String> genreComboBox;
    @FXML
    private TextField ratingField;
    @FXML
    private Button searchButton;
    @FXML
    private Button favoritesButton;
    @FXML
    private ListView<Movie> moviesListView;
    @FXML
    private Button prevPageButton;

    @FXML
    private Button nextPageButton;


    private Set<Movie> favoriteMovies = new HashSet<>();
    private int currentUiPage = 1;
    private final int apiPagesPerUiPage = 10; // Nombre de pages de l'API chargées par page de l'UI
    private final int totalApiPages = 100; // Total des pages de l'API à charger
    private final int totalPagesUi = totalApiPages / apiPagesPerUiPage;
    private Set<Movie> allMovies = new HashSet<>();
    private Map<String, Integer> genreNameToIdMap;



    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeGenreMap();
        genreComboBox.getItems().addAll(genreNameToIdMap.keySet());
        loadMovies();
        fetchAllMovies();

        moviesListView.setCellFactory(param -> new ListCell<Movie>() {
            @Override
            protected void updateItem(Movie movie, boolean empty) {
                super.updateItem(movie, empty);
                if (empty || movie == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox hBox = new HBox(10);
                    hBox.setAlignment(Pos.CENTER_LEFT);
                    hBox.setPadding(new Insets(5, 10, 5, 10));

                    VBox vBoxText = new VBox(5);
                    Label titleLabel = new Label(movie.getTitle());
                    // Utiliser extractYear pour gérer la date de sortie
                    String yearText = extractYearSafe(movie.getReleaseDate());
                    Label yearLabel = new Label(yearText);
                    vBoxText.getChildren().addAll(titleLabel, yearLabel);

                    HBox starsBox = createStarsBox(movie.getVoteAverage());

                    Button likeButton = new Button();
                    likeButton.setText(favoriteMovies.contains(movie) ? "Unlike" : "Like");
                    likeButton.setOnAction(event -> {
                        toggleFavorite(movie);
                        likeButton.setText(favoriteMovies.contains(movie) ? "Unlike" : "Like");
                        moviesListView.refresh();
                    });

                    titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
                    yearLabel.setStyle("-fx-font-size: 12px;");
                    titleLabel.setPrefWidth(200);
                    yearLabel.setPrefWidth(200);
                    starsBox.setPrefWidth(100);

                    hBox.getChildren().addAll(vBoxText, starsBox, likeButton);
                    setGraphic(hBox);

                    setOnMouseClicked(event -> {
                        if (event.getClickCount() == 1 && (!isEmpty())) {
                            handleMovieClick();
                        }
                    });
                }
            }
        });
    }

    private String extractYearSafe(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) {
            return "1890";
        }
        return releaseDate.substring(0, 4); // Extraher et retourner l'année
    }




    @FXML
    private void handleSearchButtonAction() {
        String name = nameField.getText();
        String fromYear = fromYearField.getText();
        String toYear = toYearField.getText();
        String genre = genreComboBox.getValue();
        String rating = ratingField.getText();

        List<Movie> filteredMovies = searchMovies(name, fromYear, toYear, genre, rating); // You need to implement this method.
        moviesListView.getItems().setAll(filteredMovies);
    }

    private List<Movie> searchMovies(String name, String fromYear, String toYear, String genreName, String ratingString) {
        double parsedRating = 0;
        if (ratingString != null && !ratingString.isEmpty()) {
            try {
                parsedRating = Double.parseDouble(ratingString);
            } catch (NumberFormatException e) {
                // Handle the case where the rating is not a valid double
            }
        }
        final double rating = parsedRating;

        Integer parsedGenreId = null;
        if (genreName != null && !genreName.isEmpty()) {
            parsedGenreId = getGenreIdByName(genreName);
        }
        final Integer genreId = parsedGenreId;

        return allMovies.stream()
                .filter(movie -> name == null || name.isEmpty() || movie.getTitle().toLowerCase().contains(name.toLowerCase()))
                .filter(movie -> genreId == null || Arrays.stream(movie.getGenreIds()).anyMatch(id -> id == genreId))
                .filter(movie -> rating == 0 || movie.getVoteAverage() >= rating)
                .filter(movie -> {
                    if (fromYear != null && !fromYear.isEmpty() && toYear != null && !toYear.isEmpty()) {
                        int year = extractYear(movie.getReleaseDate());
                        int from = Integer.parseInt(fromYear);
                        int to = Integer.parseInt(toYear);
                        return year >= from && year <= to;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }
    private void loadMovies() {
        List<Movie> movies = new ArrayList<>();
        // Calculez le numéro de la première page de l'API pour la page actuelle de l'UI
        int startApiPage = (currentUiPage - 1) * apiPagesPerUiPage + 1;
        for (int i = 0; i < apiPagesPerUiPage; i++) {
            int apiPage = startApiPage + i;
            movies.addAll(fetchMovies(apiPage));
        }
        moviesListView.getItems().setAll(movies);
        updateNavigationButtons();
    }

    public void fetchAllMovies() {
        OkHttpClient client = new OkHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        allMovies.clear(); // Assurez-vous que la liste est vide avant de commencer le processus de récupération.

        for (int page = 1; page <= totalApiPages; page++) {
            Request request = new Request.Builder()
                    .url("https://api.themoviedb.org/3/movie/popular?language=en-US&page=" + page + "&api_key=b8f844e585235d0341ba72bbc763ead2")
                    .get()
                    .build();

            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JsonNode rootNode = objectMapper.readTree(responseBody);
                    JsonNode resultsNode = rootNode.path("results");

                    for (JsonNode node : resultsNode) {
                        Movie movie = objectMapper.treeToValue(node, Movie.class);
                        allMovies.add(movie); // Accumulez les films de toutes les pages ici.
                    }
                } else {
                    System.out.println("Failed to get response from the API for page " + page);
                }
            } catch (IOException e) {
                e.printStackTrace();
                // Il pourrait être judicieux d'introduire une certaine forme de gestion des erreurs ou de réessayer la logique ici.
            }
        }
    }



    public List<Movie> fetchMovies(int page) {
        List<Movie> moviesOnPage = new ArrayList<>();
        OkHttpClient client = new OkHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();

        Request request = new Request.Builder()
                .url("https://api.themoviedb.org/3/movie/popular?language=en-US&page=" + page + "&api_key=b8f844e585235d0341ba72bbc763ead2")
                .get()
                .build();

        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JsonNode rootNode = objectMapper.readTree(responseBody);
                JsonNode resultsNode = rootNode.path("results");

                for (JsonNode node : resultsNode) {
                    Movie movie = objectMapper.treeToValue(node, Movie.class);
                    moviesOnPage.add(movie);
                }
            } else {
                System.out.println("Failed to get response from the API for page " + page);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return moviesOnPage;
    }




    private void initializeGenreMap() {
        genreNameToIdMap = new HashMap<>();
        genreNameToIdMap.put("Action", 28);
        genreNameToIdMap.put("Adventure", 12);
        genreNameToIdMap.put("Animation", 16);
        genreNameToIdMap.put("Comedy", 35);
        genreNameToIdMap.put("Crime", 80);
        genreNameToIdMap.put("Documentary", 99);
        genreNameToIdMap.put("Drama", 18);
        genreNameToIdMap.put("Family", 10751);
        genreNameToIdMap.put("Fantasy", 14);
        genreNameToIdMap.put("History", 36);
        genreNameToIdMap.put("Horror", 27);
        genreNameToIdMap.put("Music", 10402);
        genreNameToIdMap.put("Mystery", 9648);
        genreNameToIdMap.put("Romance", 10749);
        genreNameToIdMap.put("Science fiction", 878);
        genreNameToIdMap.put("Tv movie", 10770);
        genreNameToIdMap.put("Thriller", 53);
        genreNameToIdMap.put("War", 10752);
        genreNameToIdMap.put("Western", 37);
    }

    private Integer getGenreIdByName(String genreName) {
        if (genreName == null || genreNameToIdMap == null) {
            return null;
        }
        return genreNameToIdMap.get(genreName);
    }


    private int extractYear(String releaseDate) {
        if (releaseDate == null || releaseDate.isEmpty() || releaseDate.length() < 4) {
            return 1800; // Retourne une année par défaut si la date de sortie n'est pas valide
        }
        try {
            return Integer.parseInt(releaseDate.substring(0, 4));
        } catch (NumberFormatException e) {
            System.err.println("Erreur lors de l'extraction de l'année pour la date: " + releaseDate);
            return -1; // Retourne une valeur d'année invalide en cas d'erreur
        }
    }







    private HBox createStarsBox(double rating) {
        HBox starsBox = new HBox();
        starsBox.setSpacing(5);
        for (int i = 1; i <= 5; i++) {
            Label starLabel = new Label();
            if (i <= rating) {
                starLabel.setText("\u2605"); // Unicode solid star
            } else {
                starLabel.setText("\u2606"); // Unicode outline star
            }
            starsBox.getChildren().add(starLabel);
        }
        return starsBox;
    }




    private void toggleFavorite(Movie movie) {
        if (favoriteMovies.contains(movie)) {
            favoriteMovies.remove(movie); // Supprimer le film s'il est déjà dans les favoris
        } else {
            boolean isAlreadyFavorite = favoriteMovies.stream()
                    .anyMatch(favorite -> favorite.getId() == movie.getId());
            if (!isAlreadyFavorite) {
                favoriteMovies.add(movie); // Ajouter le film s'il n'est pas déjà dans les favoris
            }
        }
        moviesListView.refresh(); // Rafraîchir la ListView pour refléter le changement
    }


    @FXML
    private void handleFavoritesButtonAction() {
        moviesListView.getItems().setAll(favoriteMovies);
    }


    @FXML
    private void handleShowAllMoviesAction() {
        loadMovies(); // Load all movies back into the list view
    }
    @FXML
    private void restart(){
        favoriteMovies.clear();
        loadMovies();
    }
    @FXML
    private void handleMovieClick() {
        Movie selectedMovie = moviesListView.getSelectionModel().getSelectedItem();
        if (selectedMovie != null) {
            // Afficher toutes les informations du film (par exemple, dans une boîte de dialogue)
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Details du film");
            alert.setHeaderText(selectedMovie.getTitle());
            alert.setContentText(
                    "Adult: " + selectedMovie.isAdult() +'\n'+
                            "Id: " + selectedMovie.getId() + ","+'\n'+
                            "Original Language: '" + selectedMovie.getOriginalLanguage() + "'"+'\n'+
                            "Original Title: '" + selectedMovie.getOriginalTitle() + "'"+'\n'+
                            "Overview: '" + selectedMovie.getOverview() + "'"+'\n'+
                            "Popularity: " + selectedMovie.getPopularity() +'\n'+
                            "Release Date: '" + selectedMovie.getReleaseDate() + "'"+'\n'+
                            "Video: " + selectedMovie.isVideo() +'\n'+
                            "Vote Average: " + selectedMovie.getVoteAverage() +'\n'+
                            "Vote Count: " + selectedMovie.getVoteCount());
            alert.showAndWait();
        }
    }

    @FXML
    private void handlePrevPageAction() {
        if (currentUiPage > 1) {
            currentUiPage--;
            loadMovies();
        }
    }

    @FXML
    private void handleNextPageAction() {
        if (currentUiPage < totalPagesUi) {
            currentUiPage++;
            loadMovies();
        }
    }
    private void updateNavigationButtons() {
        prevPageButton.setDisable(currentUiPage <= 1);
        nextPageButton.setDisable(currentUiPage >= totalPagesUi);
    }



}
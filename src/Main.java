import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Scanner;

public class Main {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/usedmarketdb";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("=============================");
            System.out.println("\uD83D\uDCE6 중고거래 플랫폼 조회 시스템");
            System.out.println("=============================");
            System.out.println("1. 특정 회원이 등록한 상품 조회");
            System.out.println("2. 사용자가 찜한 상품 목록 조회");
            System.out.println("3. 특정 기간 내 거래 내역 조회");
            System.out.println("4. 판매자의 평균 평점 및 리뷰 수 조회");
            System.out.println("5. 특정 회원의 판매중 상품 조회");
            System.out.println("0. 종료");
            System.out.print("\n 메뉴 선택: ");

            int choice = -1;
            try {
                choice = Integer.parseInt(sc.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("숫자를 입력해주세요.\n");
                continue;
            }

            switch (choice) {
                case 1:
                    System.out.print("- 회원 ID를 입력하세요: ");
                    int userId = Integer.parseInt(sc.nextLine());
                    findProductsByUserId(userId);
                    break;
                case 2:
                    System.out.print("- 사용자 ID를 입력하세요: ");
                    int userId2 = Integer.parseInt(sc.nextLine());
                    findWishlistByUserId(userId2);
                    break;
                case 3:
                    System.out.print("- 시작일 (YYYY-MM-DD): ");
                    String startDate = sc.nextLine();
                    System.out.print("- 종료일 (YYYY-MM-DD): ");
                    String endDate = sc.nextLine();
                    findTransactionsInPeriod(startDate, endDate);
                    break;
                case 4:
                    System.out.print("- 판매자 ID들을 쉼표로 입력하세요 (예: 10,20,30): ");
                    String[] ids = sc.nextLine().split(",");
                    int[] sellerIds = Arrays.stream(ids).mapToInt(Integer::parseInt).toArray();
                    findReviewStatsBySellerIds(sellerIds);
                    break;
                case 5:
                    System.out.print("- 회원 ID를 입력하세요: ");
                    int userId5 = Integer.parseInt(sc.nextLine());
                    findActiveProductsByUser(userId5);
                    break;
                case 0:
                    System.out.println("프로그램을 종료합니다. 안녕히 가세요!");
                    return;
                default:
                    System.out.println("잘못된 메뉴 선택입니다. 다시 입력해주세요.\n");
            }
        }
    }

    public static void findProductsByUserId(int userId) {
        String query = "SELECT product_id, title, product_name, price, status, created_at " +
                "FROM Product WHERE user_id = ? ORDER BY created_at DESC";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();

                System.out.println("\n[회원 ID: " + userId + "] 등록 상품 목록");
                System.out.println("--------------------------------------------------");
                System.out.println("상품 ID | 제목 | 상품명 | 가격 | 상태 | 등록일");
                System.out.println("--------------------------------------------------");

                while (rs.next()) {
                    System.out.printf("%d | %s | %s | %d | %s | %s\n",
                            rs.getLong("product_id"),
                            rs.getString("title"),
                            rs.getString("product_name"),
                            rs.getInt("price"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toString());
                }
                System.out.println();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("쿼리 실행 중 오류 발생 (롤백됨): " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("DB 연결 실패: " + e.getMessage());
        }
    }

    public static void findWishlistByUserId(int userId) {
        String query = "SELECT p.product_id, p.title, p.price, w.created_at AS wishlist_added_at " +
                "FROM Wishlist w JOIN Product p ON w.product_id = p.product_id " +
                "WHERE w.user_id = ? ORDER BY w.created_at DESC";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();

                System.out.println("\n[사용자 ID: " + userId + "] 찜한 상품 목록");
                System.out.println("--------------------------------------------------");
                System.out.println("상품 ID | 제목 | 가격 | 찜 추가일");
                System.out.println("--------------------------------------------------");

                boolean hasResult = false;
                while (rs.next()) {
                    hasResult = true;
                    System.out.printf("%d | %s | %d | %s\n",
                            rs.getLong("product_id"),
                            rs.getString("title"),
                            rs.getInt("price"),
                            rs.getTimestamp("wishlist_added_at").toString());
                }

                if (!hasResult) {
                    System.out.println("※ 해당 사용자의 찜 목록이 존재하지 않습니다.");
                }

                System.out.println();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("쿼리 실행 중 오류 발생 (롤백됨): " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("DB 연결 실패: " + e.getMessage());
        }
    }



    public static void findTransactionsInPeriod(String startDate, String endDate) {
        if (!isValidDate(startDate) || !isValidDate(endDate)) {
            System.out.println("※ 날짜 형식이 올바르지 않습니다. (예: 2024-01-01)");
            return;
        }

        String query = "SELECT transaction_id, product_id, seller_id, buyer_id, created_at " +
                "FROM Transaction WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, startDate);
                pstmt.setString(2, endDate);
                ResultSet rs = pstmt.executeQuery();

                System.out.println("\n[거래 내역: " + startDate + " ~ " + endDate + "]");
                System.out.println("-----------------------------------------------------------");
                System.out.println("ID | 상품ID | 판매자ID | 구매자ID | 거래일");
                System.out.println("-----------------------------------------------------------");

                boolean hasResult = false;
                while (rs.next()) {
                    hasResult = true;
                    System.out.printf("%d | %d | %d | %d | %s\n",
                            rs.getLong("transaction_id"),
                            rs.getLong("product_id"),
                            rs.getLong("seller_id"),
                            rs.getLong("buyer_id"),
                            rs.getTimestamp("created_at").toString());
                }

                if (!hasResult) {
                    System.out.println("※ 해당 기간 내 거래 내역이 없습니다.");
                }

                System.out.println();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("쿼리 실행 중 오류 발생 (롤백됨): " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("DB 연결 실패: " + e.getMessage());
        }
    }

    private static boolean isValidDate(String dateStr) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setLenient(false);
        try {
            sdf.parse(dateStr);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    public static void findReviewStatsBySellerIds(int[] sellerIds) {
        if (sellerIds.length == 0) return;

        String placeholders = String.join(",", Arrays.stream(sellerIds).mapToObj(id -> "?").toArray(String[]::new));
        String query = "SELECT seller_id, AVG(rating) AS average_rating, COUNT(*) AS review_count " +
                "FROM Review WHERE seller_id IN (" + placeholders + ") GROUP BY seller_id";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                for (int i = 0; i < sellerIds.length; i++) {
                    pstmt.setInt(i + 1, sellerIds[i]);
                }
                ResultSet rs = pstmt.executeQuery();

                System.out.println("\n[판매자 리뷰 통계]");
                System.out.println("------------------------------");
                System.out.println("판매자ID | 평균 평점 | 리뷰 수");
                System.out.println("------------------------------");

                while (rs.next()) {
                    System.out.printf("%d | %.2f | %d\n",
                            rs.getInt("seller_id"),
                            rs.getDouble("average_rating"),
                            rs.getInt("review_count"));
                }
                System.out.println();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("쿼리 실행 중 오류 발생 (롤백됨): " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("DB 연결 실패: " + e.getMessage());
        }
    }

    public static void findActiveProductsByUser(int userId) {
        String query = "SELECT product_id, title, price, created_at " +
                "FROM Product WHERE user_id = ? AND status = '판매중' ORDER BY created_at DESC";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();

                System.out.println("\n[회원 ID: " + userId + "] 판매중인 상품 목록");
                System.out.println("---------------------------------------------");
                System.out.println("ID | 제목 | 가격 | 등록일");
                System.out.println("---------------------------------------------");

                while (rs.next()) {
                    System.out.printf("%d | %s | %d | %s\n",
                            rs.getLong("product_id"),
                            rs.getString("title"),
                            rs.getInt("price"),
                            rs.getTimestamp("created_at").toString());
                }
                System.out.println();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("쿼리 실행 중 오류 발생 (롤백됨): " + e.getMessage());
            }
        } catch (SQLException e) {
            System.out.println("DB 연결 실패: " + e.getMessage());
        }
    }
}

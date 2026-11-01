import psycopg2
import os
import sys

# Target local PostgreSQL database (from docker-compose.deps.yml)
# Default port is 5433, fallback to 5432 if user mapped differently
LOCAL_PORTS = [5433, 5432]
SQL_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "backup_ai_db.sql")

def try_connect():
    for port in LOCAL_PORTS:
        conn_str = f"postgresql://postgres:postgres@localhost:{port}/ai_db"
        try:
            conn = psycopg2.connect(conn_str)
            print(f"[OK] Ket noi thanh cong toi PostgreSQL tai localhost:{port}/ai_db")
            return conn, port
        except Exception as e:
            # Try default postgres database to see if ai_db needs to be created
            try:
                admin_conn = psycopg2.connect(f"postgresql://postgres:postgres@localhost:{port}/postgres")
                admin_conn.autocommit = True
                cur = admin_conn.cursor()
                cur.execute("SELECT 1 FROM pg_database WHERE datname = 'ai_db';")
                if not cur.fetchone():
                    print(f"[*] Dang tao database 'ai_db' tren port {port}...")
                    cur.execute("CREATE DATABASE ai_db;")
                admin_conn.close()
                conn = psycopg2.connect(conn_str)
                print(f"[OK] Da tao va ket noi toi database ai_db tai localhost:{port}")
                return conn, port
            except Exception:
                pass
    return None, None

def main():
    if not os.path.exists(SQL_FILE):
        print(f"[ERROR] Khong tim thay file backup: {SQL_FILE}")
        sys.exit(1)

    print("--- DANG RESTORE DU LIEU VAO CONTAINER LOCAL POSTGRES ---")
    conn, port = try_connect()
    if not conn:
        print("[ERROR] Khong the ket noi toi PostgreSQL container tai localhost:5433 hoac localhost:5432.")
        print("Hay dam bao container dang chay bang lenh:")
        print("  docker compose -f docker-compose.deps.yml up postgres-db -d")
        sys.exit(1)

    try:
        cur = conn.cursor()

        # Ensure table ai_explanations exists before inserting
        cur.execute("""
            CREATE TABLE IF NOT EXISTS ai_explanations (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                submission_id UUID NOT NULL,
                student_id UUID NOT NULL,
                category VARCHAR(30) NOT NULL DEFAULT 'FULL_ANALYSIS',
                diagnosis TEXT NOT NULL,
                suspected_location TEXT,
                actionable_hints JSONB NOT NULL,
                learning_keywords JSONB,
                telemetry_audit JSONB,
                overall_summary TEXT,
                model_name VARCHAR(50),
                prompt_tokens INTEGER,
                completion_tokens INTEGER,
                feedback_rating SMALLINT,
                created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                deleted_at TIMESTAMP WITH TIME ZONE
            );
            CREATE INDEX IF NOT EXISTS idx_ai_explain_lookup ON ai_explanations(submission_id, category);
            CREATE INDEX IF NOT EXISTS idx_ai_explain_student ON ai_explanations(student_id);
        """)
        conn.commit()

        # Read and execute entire SQL script
        with open(SQL_FILE, "r", encoding="utf-8") as f:
            sql_content = f.read()

        cur.execute(sql_content)
        conn.commit()

        cur.execute("SELECT COUNT(*) FROM ai_explanations;")
        total_rows = cur.fetchone()[0]
        print(f"[THANH CONG] Da nap toan bo script {os.path.basename(SQL_FILE)} vao co so du lieu.")
        print(f"[TONG SO DONG] Hien co {total_rows} ban ghi trong ai_explanations.")
        conn.close()

    except Exception as e:
        print(f"[ERROR] Loi trong qua trinh restore: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()

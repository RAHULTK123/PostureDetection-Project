import cv2
import mediapipe as mp
import numpy as np
import pyrebase
from datetime import datetime
import tkinter as tk
from tkinter import ttk, messagebox, font as tkFont
from tkinter.ttk import Style
from PIL import Image, ImageTk
import pygame
import threading
import queue
import time

try:
    pygame.mixer.init()
    pygame.mixer.music.load("assets/output.mp3")
    pygame_initialized = True
except pygame.error as e:
    print(f"Warning: Could not initialize pygame mixer or load sound file: {e}")
    print("Voice command functionality will be disabled.")
    pygame_initialized = False
    def voice_command_dummy():
        print("Voice command disabled (pygame error).")

firebase_config = {
    "apiKey": "AIzaSyCp7VDpSzJ9VRrU3Wcm_VQjkTs21UR2QZI",
    "authDomain": "posture-detection-e30cf.firebaseapp.com",
    "databaseURL": "https://posture-detection-e30cf-default-rtdb.asia-southeast1.firebasedatabase.app",
    "storageBucket": "posture-detection-e30cf.firebasestorage.app",
}
try:
    firebase = pyrebase.initialize_app(firebase_config)
    auth = firebase.auth()
    db = firebase.database()
    print("Firebase initialized successfully.")
except Exception as e:
    print(f"FATAL ERROR: Could not initialize Firebase: {e}")
    exit()

mp_pose = mp.solutions.pose
pose_instance = mp_pose.Pose()
mp_drawing = mp.solutions.drawing_utils

def calculate_angle(a, b, c):
    a = np.array(a)
    b = np.array(b)
    c = np.array(c)
    radians = np.arctan2(c[1] - b[1], c[0] - b[0]) - np.arctan2(a[1] - b[1], a[0] - b[0])
    angle = np.abs(radians * 180.0 / np.pi)
    if angle > 180.0: angle = 360 - angle
    return angle

class LoginSignupApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Login or Signup")
        window_width = 450
        window_height = 350
        screen_width = root.winfo_screenwidth()
        screen_height = root.winfo_screenheight()
        center_x = int(screen_width/2 - window_width / 2)
        center_y = int(screen_height/2 - window_height / 2)
        self.root.geometry(f'{window_width}x{window_height}+{center_x}+{center_y}')
        self.root.configure(bg="#f4f4f9")
        self.root.resizable(False, False)

        self.email_var = tk.StringVar()
        self.password_var = tk.StringVar()

        tk.Label(self.root, text="Posture Detection Login", font=("Helvetica", 18, "bold"), bg="#f4f4f9").pack(pady=20)

        tk.Label(self.root, text="Email:", bg="#f4f4f9", font=("Helvetica", 12)).pack(pady=(10,0))
        self.email_entry = tk.Entry(self.root, textvariable=self.email_var, font=("Helvetica", 12), width=30)
        self.email_entry.pack(pady=5)

        tk.Label(self.root, text="Password:", bg="#f4f4f9", font=("Helvetica", 12)).pack(pady=(10,0))
        self.password_entry = tk.Entry(self.root, textvariable=self.password_var, font=("Helvetica", 12), width=30, show="*")
        self.password_entry.pack(pady=5)

        self.login_button = tk.Button(self.root, text="Login", command=self.login_user, bg="#4CAF50", fg="white",
                                      font=("Helvetica", 12, "bold"), width=15, relief=tk.FLAT, pady=5)
        self.login_button.pack(pady=(20,10))

        self.signup_button = tk.Button(self.root, text="Sign Up", command=self.signup_user, bg="#2196F3", fg="white",
                                       font=("Helvetica", 12, "bold"), width=15, relief=tk.FLAT, pady=5)
        self.signup_button.pack()

        self.email_entry.focus()
        self.root.bind('<Return>', self.login_user)

    def login_user(self, event=None):
        email = self.email_var.get()
        password = self.password_var.get()
        if not email or not password:
            messagebox.showwarning("Input Error", "Please enter both email and password.")
            return

        try:
            self.login_button.config(state=tk.DISABLED)
            self.signup_button.config(state=tk.DISABLED)
            self.root.update_idletasks()

            user = auth.sign_in_with_email_and_password(email, password)
            messagebox.showinfo("Success", "Logged in successfully!")
            self.root.destroy()
            main_root = tk.Tk()
            PostureApp(main_root, user["localId"])
            main_root.mainloop()
        except Exception as e:
            print(f"Login Error: {e}")
            messagebox.showerror("Login Failed", "Invalid email or password!")
            if self.root.winfo_exists():
                 self.login_button.config(state=tk.NORMAL)
                 self.signup_button.config(state=tk.NORMAL)
        finally:
             try:
                 if self.root.winfo_exists():
                     self.login_button.config(state=tk.NORMAL)
                     self.signup_button.config(state=tk.NORMAL)
             except tk.TclError:
                 pass

    def signup_user(self):
        email = self.email_var.get()
        password = self.password_var.get()

        if not email or not password:
             messagebox.showerror("Error", "Email and password cannot be empty.")
             return
        if len(password) < 6:
             messagebox.showerror("Error", "Password must be at least 6 characters long.")
             return

        try:
            self.login_button.config(state=tk.DISABLED)
            self.signup_button.config(state=tk.DISABLED)
            self.root.update_idletasks()

            auth.create_user_with_email_and_password(email, password)
            messagebox.showinfo("Success", "Account created successfully! Please login.")
        except Exception as e:
            print(f"Signup Error: {e}")
            messagebox.showerror("Error", "Could not create account. Email might already be in use or password is too weak.")
        finally:
            self.login_button.config(state=tk.NORMAL)
            self.signup_button.config(state=tk.NORMAL)

class PostureApp:
    def __init__(self, root, user_id):
        self.root = root
        self.user_id = user_id
        self.root.title("Posture Detection Application")
        self.root.geometry("900x750")
        self.root.configure(bg="#f4f4f9")

        self.camera_index = tk.IntVar(value=0)
        self.running = False
        self.cap = None
        self.bad_posture_start_time = None
        self.bad_posture_start_time_notification = None

        self.processing_thread = None
        self.stop_event = threading.Event()
        self.results_queue = queue.Queue(maxsize=1)
        self.last_firebase_update_time = time.time()
        self.firebase_update_interval = 1.0

        if pygame_initialized:
             self.voice_command = self._play_voice_command
        else:
             global voice_command_dummy
             self.voice_command = voice_command_dummy

        self.setup_ui()
        self.root.protocol("WM_DELETE_WINDOW", self.on_closing)

    def setup_ui(self):
        header_frame = tk.Frame(self.root, bg="#283593", height=60)
        header_frame.pack(side=tk.TOP, fill=tk.X)
        tk.Label(header_frame, text="Posture Detection System", bg="#283593", fg="white", font=("Helvetica", 20, "bold")).pack(pady=10)
        self.logout_button = tk.Button(header_frame, text="Logout", command=self.logout_user, bg="#F44336", fg="white", font=("Helvetica", 10, "bold"), width=8, relief=tk.FLAT)
        self.logout_button.place(relx=1.0, rely=0.5, x=-10, y=0, anchor=tk.E)

        control_frame = tk.Frame(self.root, bg="white", height=60, pady=10)
        control_frame.pack(side=tk.TOP, fill=tk.X)
        tk.Label(control_frame, text="Select Camera:", font=("Helvetica", 12), bg="white").pack(side=tk.LEFT, padx=(20, 5))
        self.camera_dropdown = ttk.Combobox(control_frame, textvariable=self.camera_index, values=[0, 1, 2], width=5, state="readonly", font=("Helvetica", 11))
        self.camera_dropdown.pack(side=tk.LEFT, padx=5)
        self.start_button = tk.Button(control_frame, text="Start", command=self.start_detection, bg="#4CAF50", fg="white", font=("Helvetica", 12, "bold"), width=10, relief=tk.FLAT)
        self.start_button.pack(side=tk.LEFT, padx=10)
        self.stop_button = tk.Button(control_frame, text="Stop", command=self.stop_detection, bg="#F44336", fg="white", font=("Helvetica", 12, "bold"), width=10, state=tk.DISABLED, relief=tk.FLAT)
        self.stop_button.pack(side=tk.LEFT, padx=10)

        self.canvas = tk.Canvas(self.root, width=640, height=480, bg="#e0e0e0", highlightthickness=1, highlightbackground="#cccccc")
        self.canvas.pack(pady=20)

        status_frame = tk.Frame(self.root, bg="#f4f4f9")
        status_frame.pack(pady=5)
        self.status_label = tk.Label(status_frame, text="Posture Status: Not Detected", font=("Helvetica", 14, "bold"), bg="#f4f4f9")
        self.status_label.pack(side=tk.LEFT, padx=10)
        self.status_indicator = tk.Label(status_frame, width=2, height=1, bg="gray")
        self.status_indicator.pack(side=tk.LEFT)

        self.guidance_label = tk.Label(self.root, text="Guidance: Start detection to receive feedback.", font=("Helvetica", 12), bg="#f4f4f9", wraplength=800, justify=tk.CENTER)
        self.guidance_label.pack(pady=10, fill=tk.X, padx=20)

    def update_status_display(self, posture_status):
        self.status_label.config(text=f"Posture Status: {posture_status}")
        if posture_status == "Good":
            self.status_indicator.config(bg="green")
        elif posture_status == "Bad":
            self.status_indicator.config(bg="red")
        elif posture_status == "Error":
            self.status_indicator.config(bg="orange")
        else:
            self.status_indicator.config(bg="gray")

    def update_guidance_display(self, guidance_message):
        self.guidance_label.config(text=f"Guidance: {guidance_message}")

    def update_canvas_display(self, image):
        try:
            img_pil = Image.fromarray(image)
            imgtk = ImageTk.PhotoImage(image=img_pil)
            self.canvas.create_image(0, 0, anchor=tk.NW, image=imgtk)
            self.canvas.image = imgtk
        except Exception as e:
            print(f"Error updating canvas: {e}")

    def start_detection(self):
        if self.running:
            messagebox.showinfo("Info", "Detection is already running.")
            return
        try:
            self.cap = cv2.VideoCapture(self.camera_index.get())
            if not self.cap.isOpened(): raise ValueError("Failed to open camera.")
            self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
            self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
            self.cap.set(cv2.CAP_PROP_FPS, 30)
        except Exception as e:
            messagebox.showerror("Camera Error", f"Failed to open camera {self.camera_index.get()}: {e}")
            if self.cap: self.cap.release()
            self.cap = None
            return

        self.running = True
        self.stop_event.clear()
        self.processing_thread = threading.Thread(target=self._processing_loop, daemon=True)
        self.processing_thread.start()
        self._check_results_queue()

        self.start_button.config(state=tk.DISABLED)
        self.stop_button.config(state=tk.NORMAL)
        self.camera_dropdown.config(state=tk.DISABLED)
        self.update_guidance_display("Detection started. Please position yourself.")

    def stop_detection(self):
        if not self.running: return
        self.running = False
        self.stop_event.set()
        if self.processing_thread is not None:
            self.processing_thread.join(timeout=1.0)
            if self.processing_thread.is_alive(): print("Warning: Processing thread did not terminate gracefully.")
            self.processing_thread = None
        if self.cap:
            self.cap.release()
            self.cap = None

        self.canvas.delete("all")
        self.canvas.create_text(320, 240, text="Detection Stopped", font=("Helvetica", 16), fill="#546E7A")
        self.update_status_display("Not Detected")
        self.update_guidance_display("Detection stopped.")

        self.bad_posture_start_time = None
        self.bad_posture_start_time_notification = None
        while not self.results_queue.empty():
            try: self.results_queue.get_nowait()
            except queue.Empty: break

        self.start_button.config(state=tk.NORMAL)
        self.stop_button.config(state=tk.DISABLED)
        self.camera_dropdown.config(state="readonly")

    def _processing_loop(self):
        global pose_instance

        while not self.stop_event.is_set():
            if self.cap is None or not self.cap.isOpened():
                time.sleep(0.5)
                continue

            ret, frame = self.cap.read()
            if not ret:
                time.sleep(0.1)
                continue

            image = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            image.flags.writeable = False
            results = pose_instance.process(image)
            image.flags.writeable = True

            posture_status = "Not Detected"
            guidance_message = "Ensure your full upper body is visible."
            posture_notification_triggered = False

            try:
                if results.pose_landmarks:
                    landmarks = results.pose_landmarks.landmark

                    nose = [landmarks[mp_pose.PoseLandmark.NOSE.value].x, landmarks[mp_pose.PoseLandmark.NOSE.value].y]
                    l_sh = [landmarks[mp_pose.PoseLandmark.LEFT_SHOULDER.value].x, landmarks[mp_pose.PoseLandmark.LEFT_SHOULDER.value].y]
                    r_sh = [landmarks[mp_pose.PoseLandmark.RIGHT_SHOULDER.value].x, landmarks[mp_pose.PoseLandmark.RIGHT_SHOULDER.value].y]
                    l_hip = [landmarks[mp_pose.PoseLandmark.LEFT_HIP.value].x, landmarks[mp_pose.PoseLandmark.LEFT_HIP.value].y]
                    r_hip = [landmarks[mp_pose.PoseLandmark.RIGHT_HIP.value].x, landmarks[mp_pose.PoseLandmark.RIGHT_HIP.value].y]

                    shoulder_angle = calculate_angle(l_sh, nose, r_sh)
                    back_angle = calculate_angle(l_sh, l_hip, r_sh)

                    if 72 <= shoulder_angle <= 88 and 25 <= back_angle <= 36:
                        posture_status = "Good"
                        guidance_message = "Keep it up! You're sitting well."
                        self.bad_posture_start_time = None
                        self.bad_posture_start_time_notification = None
                        posture_notification_triggered = False

                    else:
                        posture_status = "Bad"
                        guidance_message = "Sit upright! Your posture needs adjustment."
                        posture_notification_triggered = True

                        current_time = datetime.now()
                        if self.bad_posture_start_time is None:
                            self.bad_posture_start_time = current_time
                        if self.bad_posture_start_time_notification is None:
                             self.bad_posture_start_time_notification = current_time
                        elif (current_time - self.bad_posture_start_time_notification).total_seconds() >= 15:
                             self.voice_command()
                             self.bad_posture_start_time_notification = current_time

                else:
                    posture_status = "Not Detected"
                    guidance_message = "Ensure your full upper body is visible."
                    self.bad_posture_start_time = None
                    self.bad_posture_start_time_notification = None
                    posture_notification_triggered = False

            except IndexError:
                 posture_status = "Error"
                 guidance_message = "Error: Could not detect all required body parts."
                 self.bad_posture_start_time = None
                 self.bad_posture_start_time_notification = None
                 posture_notification_triggered = False
            except Exception as e:
                print(f"Error processing landmarks or calculating posture: {e}")
                posture_status = "Error"
                guidance_message = "Error during processing. Check console."
                self.bad_posture_start_time = None
                self.bad_posture_start_time_notification = None
                posture_notification_triggered = False

            if results.pose_landmarks:
                mp_drawing.draw_landmarks(
                    image, results.pose_landmarks, mp_pose.POSE_CONNECTIONS,
                    landmark_drawing_spec=mp_drawing.DrawingSpec(color=(245,117,66), thickness=2, circle_radius=2),
                    connection_drawing_spec=mp_drawing.DrawingSpec(color=(245,66,230), thickness=2, circle_radius=2)
                )

            result_data = {
                "image": image,
                "status": posture_status,
                "guidance": guidance_message,
                "notification": posture_notification_triggered
            }

            try:
                if self.results_queue.full(): self.results_queue.get_nowait()
                self.results_queue.put(result_data, block=False)
            except queue.Full: pass
            except Exception as e: print(f"Error putting results in queue: {e}")

        print("Processing loop finished.")

    def _check_results_queue(self):
        if not self.running: return
        try:
            result = self.results_queue.get_nowait()
            self.update_canvas_display(result["image"])
            self.update_status_display(result["status"])
            self.update_guidance_display(result["guidance"])

            current_time = time.time()
            if current_time - self.last_firebase_update_time >= self.firebase_update_interval:
                 self.store_posture_data(result["status"], result["notification"], result["guidance"])
                 self.last_firebase_update_time = current_time

        except queue.Empty: pass
        except Exception as e: print(f"Error processing result from queue: {e}")
        self.root.after(40, self._check_results_queue)

    def store_posture_data(self, posture_status, posture_notification, guidance_message):
        def _firebase_update_task():
            try:
                current_time_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                data = {"status": posture_status, "time": current_time_str}
                notification_bool = bool(posture_notification)

                db.child("posture_logs").child(self.user_id).child("live").set(data)
                history_ref = db.child("posture_logs").child(self.user_id).child("history")
                history_ref.push(data)

                db.child("posture_logs").child(self.user_id).child("notification").set(notification_bool)
                db.child("posture_logs").child(self.user_id).child("Guidance_message").set(guidance_message)
            except Exception as e: print(f"Firebase update failed: {e}")

        firebase_thread = threading.Thread(target=_firebase_update_task, daemon=True)
        firebase_thread.start()

    def _play_voice_command(self):
        try:
            if pygame_initialized and pygame.mixer.get_init() and not pygame.mixer.music.get_busy():
                 pygame.mixer.music.play()
        except Exception as e:
            print(f"Error playing sound: {e}")

    def logout_user(self):
        self.stop_detection()
        self.root.destroy()
        root_login = tk.Tk()
        LoginSignupApp(root_login)
        root_login.mainloop()

    def on_closing(self):
        print("Closing application...")
        self.stop_detection()
        self.root.destroy()

if __name__ == "__main__":
    root_login = tk.Tk()
    app = LoginSignupApp(root_login)
    root_login.mainloop()
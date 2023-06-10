import subprocess
import subprocess as sp
from base64 import b64decode
from io import BytesIO
from time import sleep

import cv2
import numpy as np
from PIL import Image

DEPLOY_PATH = '/data/local/tmp/maa-screen.apk'
APP_MAIN = 'xyz.mufanc.maascreen.Main'
LISTEN_PORT = 34567


def deploy_apk():
    sp.call(['./gradlew', ':app:aRelease'])
    sp.call(['adb', 'push', './app/build/outputs/apk/release/app-release-unsigned.apk', DEPLOY_PATH])


class MaaScreen(object):
    def __init__(self):
        self.adb = sp.Popen(
            ['adb', 'shell', 'app_process', f'-Djava.class.path={DEPLOY_PATH}', '/system/bin', APP_MAIN],
            stdin=sp.PIPE, stdout=sp.PIPE
        )

    def command(self, *args, has_response=False):
        self.adb.stdin.write((' '.join(args) + '\n').encode())
        self.adb.stdin.flush()

        if has_response:
            output = []

            while (line := self.adb.stdout.readline().strip()) or not output:
                output.append(line)

            return b''.join(output).decode()


def simple_controller(backend: MaaScreen):
    window_name = 'MaaScreen'

    def screenshot():
        # capture screen
        response = backend.command('c', has_response=True)

        if response != 'ERR':
            return b64decode(response)

        print('ERR')
        return None

    try:
        image_data = screenshot()
        while True:
            try:
                # noinspection PyTypeChecker
                screen = np.asarray(Image.open(BytesIO(image_data)))
                screen = cv2.cvtColor(screen, cv2.COLOR_BGR2RGB)
                screen = cv2.resize(screen, (screen.shape[1] // 2, screen.shape[0] // 2))

                cv2.imshow(window_name, screen)

                while True:
                    key = cv2.waitKey(50)

                    if cv2.getWindowProperty(window_name, cv2.WND_PROP_VISIBLE) == 0:  # Window Closed
                        return

                    if key == ord('\x1b'):  # ESC
                        return

                    if key == ord('c'):
                        image_data = screenshot()
                        break
            except Exception as err:
                print(err)
                sleep(1)
                image_data = screenshot()
    finally:
        cv2.destroyWindow(window_name)


def main():
    deploy_apk()

    scrcpy = None
    backend = None

    try:
        backend = MaaScreen()

        # create virtual display: <width> <height> <dpi>
        display_id = backend.command('i', '1920', '1080', '480', has_response=True)

        print(f'displayId: {display_id}')

        scrcpy = subprocess.Popen(['scrcpy', f'--display={display_id}'])

        sleep(1)

        # start activity: <package> <class>
        backend.command('s', 'com.hypergryph.arknights', 'com.u8.sdk.U8UnityContext')

        sleep(1)

        simple_controller(backend)
    finally:
        if backend is not None:
            # exit
            backend.command('e')
            backend.adb.wait()

        if scrcpy is not None:
            scrcpy.kill()


if __name__ == '__main__':
    main()

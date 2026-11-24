# Trava Apps

App Android que bloqueia os aplicativos que você escolher. O desbloqueio é feito pelo **seu rosto, cadastrado no próprio app**, com um PIN como reserva. Quem errar várias vezes tem a **foto tirada** pela câmera frontal.

## Instalar

Baixe o `TravaApps.apk` mais recente na aba **Releases** e abra no celular. Cada envio para o branch `main` gera um APK novo pelo GitHub Actions.

## Configuração (3 passos)

1. **PIN reserva** de 4 ou 6 números.
2. **Cadastrar o rosto:** olhar de frente e virar um pouco para cada lado. São guardadas só "assinaturas" numéricas do rosto, nenhuma foto.
3. **Ativar o serviço de bloqueio** em Acessibilidade.
   - No Android 13 ou mais novo, se a opção aparecer apagada: abra Informações do app → menu ⋮ → **Permitir configurações restritas**.

Depois é só marcar os apps na lista.

## Como funciona

- Ao abrir um app marcado, a tela é coberta na hora e aparece a tela de bloqueio com a câmera frontal.
- **Rosto:** o reconhecimento facial roda 100% no celular. A detecção do rosto usa o ML Kit, e a comparação usa o modelo MobileFaceNet com TensorFlow Lite.
- **Pedir para piscar** (ativado por padrão): impede o desbloqueio com uma foto sua.
- **Intrusos:** depois de 3, 5 ou 10 erros seguidos, de rosto ou de PIN, o app guarda uma foto da câmera frontal com data, hora e app. Em seguida espera 30 segundos antes de deixar tentar de novo. As fotos ficam em **Intrusos**, onde dá para salvar na galeria ou apagar.
- **Pedir de novo:** sempre que voltar ao app, ou só depois de 1, 5 ou 15 minutos fora. Ao desligar a tela, tudo volta a pedir desbloqueio.
- O próprio Trava Apps também pede rosto ou PIN para abrir.

## Créditos

Modelo `MobileFaceNet.tflite`: [syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing](https://github.com/syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing) (licença MIT), baseado em [sirius-ai/MobileFaceNet_TF](https://github.com/sirius-ai/MobileFaceNet_TF).

package com.github.huronapp.api.constants

import com.github.huronapp.api.domain.users.{ApiKey, ApiKeyType, Email, KeyAlgorithm, KeyPair, Language, User, UserContact}
import io.chrisdavenport.fuuid.FUUID

import java.time.Instant

trait Users {

  final val ExampleUserId = FUUID.fuuid("431e092f-50ce-47eb-afbd-b806514d3f2c")

  final val ExampleUserNickName = "Alice"

  final val ExampleUserEmail = Email("alice@example.org")

  final val ExampleUserEmailDigest = "digest(alice@example.org)"

  final val ExampleUserPassword = "secret-password"

  final val ExampleUserLanguage = Language.Pl

  final val ExampleUserPasswordHash = "bcrypt(secret-password)"

  final val ExampleUser = User(ExampleUserId, ExampleUserEmailDigest, ExampleUserNickName, ExampleUserLanguage)

  final val ExampleContact = UserContact(ExampleUserId, FUUID.fuuid("6ff7f5fa-eb01-40ae-bd4b-8f9a30daf1ae"), Some("Teddy"))

  final val ExampleApiKeyId = FUUID.fuuid("b0edc95b-5bf8-4be1-b272-e91dd391beee")

  final val ExampleApiKey =
    ApiKey(
      ExampleApiKeyId,
      "ABCD",
      ExampleUserId,
      ApiKeyType.Personal,
      "My Key",
      enabled = true,
      None,
      Instant.EPOCH,
      Instant.EPOCH
    )

  final val ExamplePublicKey = """-----BEGIN PUBLIC KEY-----
                                 |MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAvFmf85mU1EBQGsGHaqOw
                                 |5v0sLS0qrX24awXaBqpwaEQJtku4kCeRXMpRaVx+e1Ja+wN6/bPvrsR8UR2n81Qb
                                 |SAv5xKqgtWFfrAXBn/BqtfgHH71K5FSZKa7DuOSXosEAOhC4D+LXk9MY6SCSXJ26
                                 |9zdO3IdOYjixZ47bB7ipiNcLewYIAoM1IjDeJ9jTPLu/cgfy0f1LVYt7tOAadzef
                                 |ybWn5fJjajTr9Aa4SO1x8mIqPwZ6cqtroz2PGTLfI8ZPaVcNzkijyi9+H7B4fdQ1
                                 |Cy+pFjDpSogOrrPvaJH+7b0KFgfc/blW0FeVWIHQP/jC4PLFojX87LXp/TnHKWRO
                                 |5wUdpjC9B6Cg7gGuqwqpE5zZFgydY3hocOoQojO0HY8Y/S05lojYIw/GewS1Gd4G
                                 |+EdTZVdnCxIVkXf5hRpxGVGn6wzRU/QaKdGjLDM1HHJDGvnsBbhfirXZQxe8z+LV
                                 |9Yb3Z04fj2M8NBzEs3xU4CH9D5DQaJ6PlFJVOg70p+aZqfgbci74fSTUMZ3+/uw9
                                 |Tp3zuEJRLXbRwESvjzGbgKLl4+WEmNuWr/iH5J8TUPSTwoPbJ0m1z7BMaFlfF1Ee
                                 |8CQFtdlFYL+ZfEI42A0lkGDDRCvxzyZ2IvD7LT8oIL0KShOFfUMNQBgVXKDLXnXq
                                 |3LAscXiZfu7N5hDvOxMzvWMCAwEAAQ==
                                 |-----END PUBLIC KEY-----""".stripMargin

  final val ExamplePrivateKey =
    "AES-CBC:0a7d4dd76ba8682c903db57bc397bfc5:36c0457e31733fe1bf7e4b07b65d5142cff1b4a93220ff3ef29f29bc6b28a74ded1d2dfb743c21bc88f9da77567ef54060abb98df1d0eeef584876003f7f4b5d0f79bf468b1520c06dcd80bc10813ebb3e56b3b1c0f5897a0e52dbe204de130659c032e8886ca838777153b5f526ae31192de88b1da3ff2d3bd98c3593b2ab299c3b6987fc22deac53813d93e1b7ab4873bf6a13b5498fc29febd23a6996cb019f8beb3beae0df0a7c89ad30a6f88eae52d2d19644a4ff42c5cdd27acda71fbf30d58be012f1872e510a1ef4729723e78d1881f6fcbe12c190bf4e2071b4f4e9cbb0bcfd3894ea143821050f5797473a77d782a84791675989794742aed23b394da2c37b1918d448ca76140cc1aa4419f2574c670711f6ab36be0ac1faa3a1b5247c774a07fb94f7bdb438cc65d8f603ed163498559971f3d116f5dc1fe1bc8ec258756f03d10ea8c90242cc3c0eb5c7956ec2903e1f9a0f317788a97e7f0d09740589db41d269058172ac1b8fb7e2f5048ec37e078f93fd1c8d0d79f3c92beac1a40c10ae09eb6ab5a082b48bd8338ac3596fd292e6a40fbbe6bf68bec19788edde2167a7426f95762185b317bc1228fedacbf4d0b8ca794cfacf8b25a72d37ed1ccf69dc64cc15442dff87c494970041760d94e09e3e3df0ebc232e7e29d959e36913643285b5fdf2a15202a310a049ec9a8aa361a936ce4b0265e8aacd80a96daee32d323311441ac6cb2022c38586b0eef5f5a29594a1ca5f339635a8071a44626f69430ccd412604ac75a0bc694459a4e7fea0c60cd4171bc62a6b3ecc3c4dfcf423f717efbe364a54d7b56446856015a9f1f7c9eeb1038b9695a300fb5e65512a9a03c6e714f77fc44f19bfca5d88745174819b9eee15e82718232d02e1234a29a849d3560a62009968af543a3166124e1610e04affdae1b6b59db52c6257e970dd45dd66b31340dab8b6912a0bcd8c2fb996c087d1fb5c4fe004f45efcb020a0a42095bfd182ad157c55e075e58f9fcd5f6c19d4a9265445fa0293a016f477b05aa472201f49b9149673286361e0478787104cdb999f6ccd53d931bd79f648fd1557fc6f360252322baebb4ad0de28067ffd971a80ce420b5d8bd12eaf3f0266416ddf2d0beb3fe299154a2ed1b3bb78a66f7785057ce6e382bd426f1e20ba56f1854dcc9f62eea5df5bbac0fb293a063654063d2db716950414f44b29b889f3136e867d17d3b321be721e78e92ac4b16a97ddb4cab0db79ef86ba6aa08c8528b03e41750b2fc83f8f0c3309e45eb381185ef901a4d930d138d5163d38b4e986455f86e7f09952120417f437a3f4503df5f5e6529808eecaa0cb948b7b4a876dde01ed0c984d2c42615d198cbb5b72f352ba55589585c9b443867f5bc68b363626c832033a5d3c67657cec3dbb19c90ea25fd5750347a281e4d2b0cadbaf398685dd9571c3ad8070dd0209370190e98a15d173001ec51a60fdb5ad72eabbd8bf16a1e939a420b5840c8965b7b7972b1c6debd8b8faad89177c2f0d79902a6f467362e34536686b6c871facb0e301887449ee519475405e5d104973e262c28554c94dc5b82451d496cd6fdaac788dd4ebeff12a3738a2dc98cda3ca8043f39e7ac1ab4702c3d6f5d43e9f38f2b589688aad3fc4d77432e469fd975b242334feacf6b2e0fcad16158352b52c19381c9a824035e90a945cb19cbfeee2c78de9d8673b24880a9e2f46d97f16d01fbd0660d5c19f3e8240423e92eb7a02400b758c7dbd18aaf37879209836f942b3a839131a1a0265d2e6d83cf524556619ba1abc1e959a2076b23cd1a0bfff070b26df0ebfb60e098bc3bb1220c2db778819375d6263935f327c13d6dd2f42c75797a35c49d6636dd6427f5559d16834b0f051c4c92e926a3d5725decd2678d8e70669194e4deeea26357ecfcad72acaaefc260a11f7272368a5758d0039d3a4adfe3bf9a5f8859c4c28a0d15a80dd9d71b09c69bbfdc9e58b7758bf0d9196db4abd00b785a2222d331f91fb240cd01e76f8a78ff34cd0ba065c4bb3077ff3061663aa45dfc95ce6babf7754d96cc44e8dbe899a0d4bcec32e0d2a5ea6d8e09d871565518c3aabe2e68b01dd03d9a88890cea7fc1ea12517ca8d96e5869a7393b521ccd375f93e25ccbb1a9a2d9502c465fdffc42407da41e1367f750ca2f7e7b877e523b49bd26b4de7c12fafcd652d58eecf91bd98f3f9a3a4acb01bba417c0805527bcecc0dd9ad74cdaa9fd2564cbfa4b0a5496243a0b5ddf72f97c07a05ec5cc3102961e0ea57c526f47c69f9ec15f9443ebe02a185c4cd1c109502e6490de342ece87683349c0a4dd167e867d224b4339ea687e2b602bf69c41f320f4bc688b9105de43e1a0dac61e531840d22754df32bb102dc45e7db46b018db82506229332170854a4721530ca7c9db6487be0fd758ee3687b83239cfbefd64bca7a23cde3a3deab279d9405cae8c56213d6236396b47f4452e3ae1025083faa6db2e99a85b089d9269f220ba6633fb4f5ae9e07c46821fbfba6fea2acafb04bf10c81c88f6454c0da64bf4eada4999741499e0502998060c6a8744601ab4a7cb9d0fdc5c3dd6116fe254308ba8e358706c4814ea6fa49c232536e79e94a4208dfb72d2edb794a0848ca3033a4d0c54b5e6afc576729ca2963c991c00de71b687737be5b0c04f6ff2797f006806cf34e6a333daed296083ed075d82a11b1e38fcbd59f59a04f83b58c77011caf66e1cafcdb19256ce663b5725905ea962e300c40fd2b17e36d86dcd39b353f08dbdc52a848aea14179e73f36313be25f7db88fca324a5f89a341d820765261280fd4173de036919e2836b1cc46af50d6b0f67a72a71ff40a244b529a11685773b32a93d3afd3b43f5bc5e386c5e8bab6ad1a067bf0ea8a6e57ac11808725b1671e01c5d68676b7ed179e1b5ea4b902aa8cc5aa09e01c5740d6af866c15bf3749ebe65b026c4a0777596f2f028ab4c4c46c15d067193be7a0dfa6c4603e4315699184a1e5682580268aa44836d349b0683b94ed932a0f233dcbc6f85b6d29351a57f102555d1178e9e9d764cdaedacdfdc9a4243961a127deaa183697b5b901ed5b36d67c74f3964d7d27168aeb0b34779f1c9812bcb71d3c2cd511f9bd1551a654b52985a566ead969f8b2396b81c2fd5b841b7588c3210ab4c7caa79c5f9e9c77f0eb9f57b7caa60be8206e8b69866a997b5d501185d065cf6e8e80824c302552352eff5fac5c56d755572195fd46165607666034d0e1db393e68b6cad321b5d68fde6c4a40bfe2dba67baf2d25806bb80a525658c4e967cd6ff1692c5fe2b8880d23e18ec366819a2f29138d37a9003265097a0ec0735bf42554ede94f9243b2252b15ee047ff4863718489144793f19f265df80366933afbed7018968c3be05612c5eb6fe3e541ca785b0749e10955b13aa3d7731ec28418232e6163300fa74a1b6b894d19cbce8ae1395b36d708d117bfe0e1ee699168b6d79862ca48ebf06ae622f4ab248f5583c72a1fbd2f5eefc5f1023bf208ccd3891147c69cd96557e3d4dfb0c51a4be84b471692c20ea63a5ccd4e987bd30377380235e1ca9572c3052ed62ee2f14d5cb3c97f5ed6ae51e73d497278f8f111b656601eef4bc7c38997b5b411794ae14b9dd00efc53be4e262e208a99711c71460d7c5e0a87d4aad3fdd12bc7e3394c40353c1b757a09aeb1589f7d0b727807abba1a31f80d0bc7e9c6af9649611a7df406d145cfea106ffab0e8e0795dbad528fc75daab41540178dadd7e5feb00f67f1bd7b26cb188d382ff15155c76bf86f65ed227cd59db453785839177da390a2514d5a938b28a7751835064a230845db9bf8ed1f3ffb39d41f6ad6d0b64e4faae59dba47e4f5034cd2cc43da199f519f9bff42a9163912cf3ab2911d2b8f86771ad9d19b3eb32dd004c8b888efd9c972b2703794cbaa7460e5f72e754a471729bf2a8968f70e551b7241119ff9a3c41e846d24060e7ced3fb58b5e8d448ec654a6abd4d1c3ca3c17f650792adbe75a1c3ff36ed14213757136e07bc7f25d48f2a64200d2afe5009449f1467f23cf5503cd49c20cf3625f57debbe58cfa7cbd90199beab324be05d98958b3e939c79484d58c2b6c72188cfb93fba713b316740b754754181b3629fc82fc6e20b917823e730a9483d73f6fc278bfc06bcc8248aded7e0c4364b3fafa4e38af4561f2cea08ea8165240c4c433127174ab28be0cc54c15edb78d233879d8f27c14fe23574b715ad7dd8371a0deb80ac2b0b15e9a6ff63a98856f3921123e118aa8f1513c0ea9a3c5279a2179060b5c159fea77fb8b86a7ad21556b0e7b7bfafa134028baa5b01109bb8a7cda9180eaf795b4dacba3c330bc048d3565b1789271bf736b3a408612e14fce1a154e0fb3416e58202bc470d9c429199d997a97f59a9889e7d57895433f3bb55d84fc9cfe3b9baad111660234f3cb08a75654310bac90963a2485b6acc88ad1ab0bb1b9d8dc5be6f654e38482a35fc9a065228624b3e317b73a7dc2af0098a5fb17b144b2452a8bb94d197f7af0f873e8cf663fe85a689b3c569af32dd8062c5763338554209b9cc385624c9f98042c242cea7d0c75ecf245fc4bbb5fce82a95ea339244016715c7d"

  final val ExampleKeyPairId = FUUID.fuuid("53b729e6-8fb3-4446-8d02-72edeb7f868f")

  final val ExampleKeyPair = KeyPair(ExampleKeyPairId, ExampleUserId, KeyAlgorithm.Rsa, ExamplePublicKey, ExamplePrivateKey)

}
